import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONObject;

public class PTTP {
  static Logger logger = LoggerFactory.getLogger(PTTP.class);
	
  //获取PPk资源并解码得到完整的结构化数据块（原文返回，不处理301/302等转向）
  public static JSONObject  fetchPPkURI(String uri){
    logger.info("\n============================================");
    logger.info("fetchPPkURI()\n-->  "+uri );
    logger.info("============================================");
    
    JSONObject  obj_decoded_chunk=null;
    try{
      //解析URI组成区段
      PPkUriParts obj_uri_parts = ODIN.splitPPkURI(uri);
      
      if(obj_uri_parts==null){
         logger.info("fetchPPkURI() meet invalid ppk-uri:"+uri);
         return null;
      }
      
      //优先尝试读取缓存
      obj_decoded_chunk = readCache( obj_uri_parts.format_uri );
      if(obj_decoded_chunk!=null){
         //logger.info("fetchPPkURI() use cache for "+uri);
         return obj_decoded_chunk;
      }

      //区分根标识或扩展标识获得对应的内容数据块
      if(obj_uri_parts.parent_odin_path.length()==0)
      { //resource is root ODIN 
        obj_decoded_chunk = getChunkOfRootODIN( obj_uri_parts );
      } else {//sub ODIN
        obj_decoded_chunk = getChunkOfExtODIN( obj_uri_parts );
      }
      
      if( obj_decoded_chunk!=null && obj_decoded_chunk.optInt(Config.JSON_KEY_PPK_VALIDATION) != Config.PTTP_VALIDATION_ERROR){
        //缓存处理
        saveCache(  obj_uri_parts.format_uri , obj_decoded_chunk );
      }else{
        logger.info("fetchPPkURI() meet invalid chunk");
      }
    }catch(Exception e){
      //e.printStackTrace();
      logger.info("fetchPPkURI("+uri+") error:"+e.toString());
    }

    return obj_decoded_chunk;
  }
  
  //获取PPk资源的内容正文
  //会自动处理301/302转向得到最终内容
  public static JSONObject getPPkResource(String ppk_uri){
      //注意：目前只支持多一层自动映射转向，不递归更多层处理，以避免可能的循环死锁
      return getPPkResource(ppk_uri,1);
  };
  
  protected static JSONObject getPPkResource(String ppk_uri,int hop){
    hop -- ;
    
    /*
    JSONObject obj_ap_resp=PTTP.fetchPPkURI(uri);
    if(obj_ap_resp==null)
        return null;
    
    return obj_ap_resp.optString(Config.JSON_KEY_ORIGINAL_RESP,"ERROR:Invalid PTTP data!");
    */
    JSONObject obj_decoded_chunk=null;
    
    try{
      obj_decoded_chunk = fetchPPkURI( ppk_uri );
      
      if(obj_decoded_chunk==null){
        return null;   
      }
    
      int validcode=obj_decoded_chunk.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_ERROR);
      if(validcode==Config.PTTP_VALIDATION_ERROR){
        obj_decoded_chunk=updateDecodedChunk(
                            obj_decoded_chunk,
                            Config.PTTP_STATUS_CODE_LOCAL_ERROR,   
                            "text/html",
                            "<font color='#F00'>PPk valiade failed!</font>".getBytes()
                          );  
      }else{
        int status_code = obj_decoded_chunk.getInt(Config.PTTP_KEY_STATUS_CODE);
        if(status_code==301 || status_code==302){
            if(hop<0){
                /*
                obj_decoded_chunk=updateDecodedChunk(
                                    obj_decoded_chunk,
                                    Config.PTTP_STATUS_CODE_LOCAL_ERROR,   
                                    "text/html",
                                    "<font color='#F00'>Exceed redirect-hop limit </font>".getBytes()
                                  );  
                */
                //超出允许的自动跳转深度时，提前返回，由浏览器或客户端进一步显式处理
                return obj_decoded_chunk;        
            }else{
                String to_uri = new String( (byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES) );

                if(to_uri.startsWith(Config.PPK_URI_PREFIX)){ //转向到PPk网址
                    int tmp_posn = to_uri.indexOf(Config.PPK_URI_RESOURCE_MARK);
                    String to_prefix = (tmp_posn<0) ? to_uri : to_uri.substring(0,tmp_posn);

                    String from_uri = obj_decoded_chunk.getString(Config.JSON_KEY_PPK_URI);
                    tmp_posn = from_uri.indexOf(Config.PPK_URI_RESOURCE_MARK);
                    String from_prefix = (tmp_posn<0) ? from_uri : from_uri.substring(0,tmp_posn);

                    String new_req_uri=
                          from_prefix.equals(to_prefix)
                        ? to_uri  //转向前后路径前缀相同时
                        : ppk_uri.replace( from_prefix,to_prefix )
                        ;

                    System.out.println("!!!!!!!!!!!!!!!!!!!!!\ngetPPkContent() process redirect");
                    System.out.println("from_uri="+from_uri);
                    System.out.println("to_uri="+to_uri);
                    System.out.println("FROM_PREFIX="+from_prefix);
                    System.out.println("TO_PREFIX="+to_prefix);
                    System.out.println("req_uri="+ppk_uri);
                    System.out.println("new_uri="+new_req_uri);
                    System.out.println("^^^^^^^^^^^^^^^^^^^^^\n");
                    
                    if( new_req_uri.equals(ppk_uri) ){ //转向到重复地址时报错
                        obj_decoded_chunk=updateDecodedChunk(
                                obj_decoded_chunk,
                                Config.PTTP_STATUS_CODE_LOCAL_ERROR, 
                                "text/html",
                                ("<font color='#F00'>Meet loop redirect : "+HtmlRegexpUtil.filterHtml(ppk_uri)+" -> "+HtmlRegexpUtil.filterHtml(new_req_uri)+"</font>").getBytes()
                              );  
                    }else{
                        return getPPkResource(new_req_uri,hop);
                    }
                }else{
                    //ap_resp_content = Util.fetchUriContent(dest_url);
                    //其它类型网址待完善，需根据应答结果数据类型来更新STATUS_CODE,CHUNK_TYPE和CHUNK_BYTES等字段
                    //暂时原样返回
                }
            }
        }
      }

      obj_decoded_chunk.remove(Config.JSON_KEY_ORIGINAL_RESP);
    }catch (Exception e) {
      logger.info("getPPkContent("+ppk_uri+") error: "+e.toString());
      e.printStackTrace();
    }
    
    return obj_decoded_chunk;
  }
  
  //更新已解码的结构化数据块
  protected static JSONObject updateDecodedChunk(JSONObject obj_decoded_chunk,int new_code,String new_type,byte[] new_chunk_bytes)
  {
    try{
        obj_decoded_chunk.put(Config.PTTP_KEY_STATUS_CODE,new_code); 
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_TYPE,new_type) ;
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_BYTES,new_chunk_bytes);
        
        return obj_decoded_chunk;
    }catch (Exception e) {
        logger.info("updateDecodedChunk error: "+e.toString());
        e.printStackTrace();
        return null;
    }
  }
  
  //生成扩展标识内容数据块
  protected static JSONObject getChunkOfExtODIN( PPkUriParts obj_uri_parts)
  {
      System.out.println("### getChunkOfExtODIN() uri="+ obj_uri_parts.format_uri );
      
      try{
        //先获取由父标识定义的内容验证设置
        String parent_odin_uri = Config.PPK_URI_PREFIX+obj_uri_parts.parent_odin_path+Config.PPK_URI_RESOURCE_MARK;
        
        JSONObject parent_odin_chunk = resolveParentODIN( parent_odin_uri );
        
        if(parent_odin_chunk==null)
            return null;
        
        //当父标识应答转向映射到新地址等异常状态的处理
        int status_code = parent_odin_chunk.getInt(Config.PTTP_KEY_STATUS_CODE);
        if(status_code>=300 && status_code<=399){
            return parent_odin_chunk;
        }else if(status_code!=Config.PTTP_STATUS_CODE_OK){
            return null;
        }

        JSONObject parent_odin_set = new  JSONObject( new String( (byte[])parent_odin_chunk.opt(Config.JSON_KEY_CHUNK_BYTES) ) );
        
        //检查ap_set和vd_set参数是否存在，如不存在则相应自动继承使用更上一级父标识的ap_set和vd_set,20181017
        if( (parent_odin_set.isNull("ap_set") || parent_odin_set.isNull("vd_set")) 
            &&  !parent_odin_chunk.isNull("parent_odin_set") ){

          JSONObject grandparent_odin_set = parent_odin_chunk.getJSONObject("parent_odin_set");
          
          if(parent_odin_set.isNull("ap_set")){
            //System.out.println("Meet null ap_set and try use parent ap_set");
            parent_odin_set.put("ap_set",grandparent_odin_set.optJSONObject("ap_set"));
          }
          
          if(parent_odin_set.isNull("vd_set")){
            //System.out.println("Meet null vd_set and try use parent vd_set");
            parent_odin_set.put("vd_set",grandparent_odin_set.optJSONObject("vd_set"));
          }
        }

        if( !parent_odin_set.isNull("ap_set")  ){
            JSONObject obj_decoded_chunk=null;
            JSONObject ap_set = parent_odin_set.getJSONObject("ap_set");
        
            //遍历标识设置的AP节点列表
            for(Iterator it = ap_set.keys(); it!=null && it.hasNext(); ) { 
              String ap_id=(String)it.next();
              JSONObject ap_record=ap_set.getJSONObject(ap_id);
              
              obj_decoded_chunk=fetchAndValidationAP(obj_uri_parts.format_uri,null,ap_record,parent_odin_set.optJSONObject("vd_set"));

              if(obj_decoded_chunk!=null){
                if( obj_decoded_chunk.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_ERROR) != Config.PTTP_VALIDATION_ERROR ){
                   //获得有效AP应答时结束遍历
                   break;
                }
              }
            }
            
            if(obj_decoded_chunk!=null && obj_decoded_chunk.optInt(Config.JSON_KEY_PPK_VALIDATION) != Config.PTTP_VALIDATION_ERROR){
                //需要完善，对于parent_odin_set加上缓存过期时间控制，注意继承关系，最好是根据父节点内容来动态获取，避免根标识已更新，但子标识内容仍用旧的验证参数
                obj_decoded_chunk.put("parent_odin_set",parent_odin_set);
            }
            
            return obj_decoded_chunk;
        }

      }catch(Exception e){
        logger.info("getChunkOfExtODIN("+obj_uri_parts.format_uri+") error:"+e.toString());
        
      }
      
      return null;
  }
  
  protected static JSONObject  resolveParentODIN(String parent_odin_uri){
    JSONObject parent_odin_chunk = null;
    try{
        parent_odin_chunk = fetchPPkURI( parent_odin_uri ); 
        
        //验证出错的解析结果无效
        if( parent_odin_chunk==null 
           || parent_odin_chunk.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_ERROR) == Config.PTTP_VALIDATION_ERROR ){
          logger.info("resolveParentODIN() meet invalid odin : "+parent_odin_uri);
          return null;
        }
    }catch(Exception e){
        logger.info("resolveParentODIN() meet error for "+parent_odin_uri);
        //e.printStackTrace();
    }

    return  parent_odin_chunk;
  }
  
  //获得根标识解析结果数据块
  protected static JSONObject getChunkOfRootODIN( PPkUriParts obj_uri_parts )
  {
      System.out.println("### getChunkOfRootODIN() uri="+obj_uri_parts.format_uri);
      
      if( ! "".equals(obj_uri_parts.resource_versoin) ) {
    	  //根标识目前不支持带版本
    	  return genLocalRespChunk(
    			  obj_uri_parts.format_uri,
                  Config.PTTP_STATUS_CODE_LOCAL_ERROR,
                  "text/html",
                  "The root odin version not supported!",
                  Config.PTTP_VALIDATION_IGNORED,
                  Util.getNowTimestamp( ),
                  Config.IGNORE_CACHE_AS_LATEST,
                  "local",
                  null
              );
      }
      
      String root_odin=obj_uri_parts.resource_id;
      
      JSONObject odin_set = null; 
      
      try{
   	    odin_set=ODIN.getRootOdinSet(root_odin);
        if(odin_set==null){
            //logger.info("getChunkOfRootODIN() meet invalid root odin:"+root_odin);
            //return null;
            
            //当指定标识在链上不存在时，PTTP协议尝试以兼容传统域名方式处理
            //默认传统域名的http/https网址就是其AP，暂时忽略验证vd_set，后续可进一步升级支持HTTPS相关证书的签名机制

            odin_set = new JSONObject();
            JSONObject ap_set = new JSONObject();
            JSONObject default_ap = new JSONObject();

            String default_url = "http://"+root_odin+"/";
            odin_set.put(Config.ODIN_BASE_SET_PNS_URL,default_url); //默认提供PNS解析，进一步映射到提供具体内容服务的节点
            
            default_ap.put("url",default_url);
            ap_set.put("0",default_ap);
            
            default_url = "https://"+root_odin+"/";
            default_ap.put("url","http://"+root_odin+"/");
            ap_set.put("1",default_ap);
            
            odin_set.put("ap_set",ap_set);

            odin_set.put("ver",Config.ODIN_PROTOCOL_VER);
            odin_set.put("title",root_odin);
            
            //System.out.println("getChunkOfRootODIN() compatibility with legacy domain : "+root_odin +"\n compatible odin_set= "+odin_set.toString() );
        }

        String cache_as_latest =  Config.DEFAULT_CACHE_AS_LATEST ;
        
        String pns_url = odin_set.optString(Config.ODIN_BASE_SET_PNS_URL,"").trim();
        if(pns_url.length()>0){
            //设置了有效的标识托管服务
            JSONObject merged_odin_set = resolvePeerNameServiceForRootODIN( pns_url,  obj_uri_parts.format_uri, odin_set );
            
            if(merged_odin_set!=null){
                odin_set = merged_odin_set;
            }else{
                //当托管服务不可用时，使用该ODIN标识在链上的保留设置作为临时备用， 但标记为不缓存
                cache_as_latest =  Config.IGNORE_CACHE_AS_LATEST ;
            }
        }
        
        //本地直接生成该ODIN根标识对应的配置信息
        String chunk_content=odin_set.toString();
        
        JSONObject obj_chunk_metainfo=new JSONObject();
        obj_chunk_metainfo.put(Config.PTTP_KEY_STATUS_CODE,Config.PTTP_STATUS_CODE_OK);
        obj_chunk_metainfo.put(Config.PTTP_KEY_STATUS_DETAIL,"OK");
        obj_chunk_metainfo.put(Config.PTTP_KEY_CONTENT_TYPE, "text/json"  );
        obj_chunk_metainfo.put(Config.PTTP_KEY_CONTENT_LENGTH, chunk_content.length()  );
        //obj_chunk_metainfo.put("chunk_index", 0 );
        //obj_chunk_metainfo.put("chunk_count", 1 );
        
        JSONObject obj_newest_ap_data=new JSONObject();
        obj_newest_ap_data.put(Config.PTTP_KEY_VER,Config.PTTP_PROTOCOL_VER);
        obj_newest_ap_data.put(Config.PTTP_KEY_SPEC,Config.PTTP_KEY_SPEC_NONE);
        obj_newest_ap_data.put(Config.PTTP_KEY_URI,obj_uri_parts.format_uri);
        obj_newest_ap_data.put(Config.PTTP_KEY_METAINFO,obj_chunk_metainfo.toString());
        obj_newest_ap_data.put(Config.PTTP_KEY_CONTENT,chunk_content);
        obj_newest_ap_data.put(Config.PTTP_KEY_SIGNATURE,"");
        
        return genLocalRespChunk(
        		obj_uri_parts.format_uri,
                Config.PTTP_STATUS_CODE_OK,
                "text/json",
                chunk_content,
                Config.PTTP_VALIDATION_OK,
                Util.getNowTimestamp( ),
                cache_as_latest,
                "",
                obj_newest_ap_data.toString()
            );
    }catch(Exception e){
        logger.info("getChunkOfRootODIN("+obj_uri_parts.format_uri+","+root_odin+") error:"+e.toString());
        return null;
    }
  }
  
  protected static JSONObject  resolvePeerNameServiceForRootODIN(String pns_url,  String odin_uri, JSONObject obj_odin_local_set ){
    //logger.info("resolvePeerNameServiceForRootODIN\n pns_url="+pns_url+"\n odin_uri="+odin_uri+"\n obj_odin_local_set="+obj_odin_local_set.toString() );
    
    JSONObject obj_decoded_pns_resp = fetchAndValidationAP(odin_uri, null, pns_url, obj_odin_local_set.optJSONObject("vd_set"));
    
    if(obj_decoded_pns_resp==null 
      || obj_decoded_pns_resp.optInt(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_ERROR) == Config.PTTP_VALIDATION_ERROR){
       //未获得有效的托管服务应答时
       logger.info("resolvePeerNameServiceForRootODIN failed!");
       return null;
    }
    
    try{
        byte[] pns_content_bytes= (byte[])obj_decoded_pns_resp.opt(Config.JSON_KEY_CHUNK_BYTES) ;
        String str_pns_setting = new String( pns_content_bytes );
        //logger.info("resolvePeerNameServiceForRootODIN str_pns_setting="+ str_pns_setting.substring(0,200)+"..." );
         
        JSONObject obj_pns_setting = new JSONObject( str_pns_setting  );

        //遍历合并标识设置参数
        for(Iterator it = obj_pns_setting.keys(); it!=null && it.hasNext(); ) { 
            String pns_key=(String)it.next();
            
            if(  !pns_key.equals(Config.ODIN_BASE_SET_REGISTER)
              && !pns_key.equals(Config.ODIN_BASE_SET_ADMIN)
              && !pns_key.equals(Config.ODIN_BASE_SET_AUTH)
              && !pns_key.equals(Config.ODIN_BASE_SET_PNS_URL)
            ){
               //排除敏感基础字段（只以BTC链上数据为准），合并PNS的设置数据
                obj_odin_local_set.put(pns_key,obj_pns_setting.get(pns_key));
            }
        }
        
        //将"pns_url"字段名改为"invoked_pns_url"表示已经调用pns解析处理，避免重复解析
        obj_odin_local_set.put("invoked_pns_url", obj_odin_local_set.getString(Config.ODIN_BASE_SET_PNS_URL));
        obj_odin_local_set.remove(Config.ODIN_BASE_SET_PNS_URL);
        
    }catch(Exception e){
        logger.info("resolvePeerNameServiceForRootODIN error: "+e.toString());
        //e.printStackTrace();
        return null;
    }
    
    //logger.info("resolvePeerNameServiceForRootODIN merged_set="+obj_odin_local_set.toString() );
    return  obj_odin_local_set;
  }
  
  
//写入缓存
  protected static boolean saveCache( String req_uri, JSONObject obj_decoded_chunk  )
  {
      try{
        String ap_resp_ppk_uri = obj_decoded_chunk.optString(Config.JSON_KEY_PPK_URI);
        
        ap_resp_ppk_uri = ODIN.formatPPkURI(ap_resp_ppk_uri);
        if(ap_resp_ppk_uri==null) //不规范的URI将不被缓存
            return false;
        
        int cache_as_latest_seconds = 0 ;
        String  cache_as_latest = obj_decoded_chunk.optString(Config.PTTP_KEY_CACHE_AS_LATEST,"");
            
        if(cache_as_latest.length()>0){ //不指定策略时缺省不缓存
            int tmp_posn = cache_as_latest.indexOf("=");
            if(tmp_posn>0){
                cache_as_latest_seconds = Integer.parseInt( cache_as_latest.substring(tmp_posn+1,cache_as_latest.length()) );
            }
        }
        
        //req_uri = ODIN.formatPPkURI(req_uri);
        String resource_ver = ODIN.getPPkResourceVer(ap_resp_ppk_uri);
        String latest_uri = ODIN.getLastestPPkURI(ap_resp_ppk_uri);
        
        long iat_utc = obj_decoded_chunk.optLong(Config.PTTP_KEY_IAT,0);
        int status_code = obj_decoded_chunk.optInt(Config.PTTP_KEY_STATUS_CODE);
        byte[] chunk_content_bytes = (byte[])obj_decoded_chunk.opt(Config.JSON_KEY_CHUNK_BYTES);
        
        //将byte[]转换为hex格式字符串保存到缓存
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_BYTES,Util.bytesToHexString(chunk_content_bytes));

        
        if( resource_ver.length()>0)
        {   //如果应答数据块的标识带有版本，则区分状态码缓存该应答数据块
            long exp_utc=0;
            long now = Util.getNowTimestamp();
            
            if(status_code==200||status_code==301){
                //带版本的有效数据块过期时间戳默认设为特别值，会缓存尽可能长的时间
                exp_utc = Config.CACHE_NO_EXP_UTC;
            }else if(status_code>=302&&status_code<=399){
                //除301外的其它3XX状态码，本地缓存时间建议不少于1小时
                exp_utc = now + 3600 ;
            }else{
                //对于其他状态，建议缓存10分钟。
                exp_utc = now + 600 ;
            }
            
            obj_decoded_chunk.put( Config.JSON_KEY_EXP_UTC ,exp_utc );
            NetCache.saveNetCache( ap_resp_ppk_uri, obj_decoded_chunk.toString( )  );
            
            //进一步判断需要处理按最新版本缓存的情况
            if(cache_as_latest_seconds>0 ){
                if( !existNewestCache(latest_uri,resource_ver,iat_utc) ){
                    //产生302转向映射到已保存的有版本标识缓存，在下次请求最新版本时可复用
                    JSONObject obj_chunk_latest = genLocalRespChunk(
                        latest_uri,
                        302,
                        "text/html",
                        ap_resp_ppk_uri,
                        Config.PTTP_VALIDATION_OK,
                        iat_utc,
                        cache_as_latest,
                        "",
                        null
                    );
                    
                    //将byte[]转换为hex格式字符串保存到缓存
                    obj_chunk_latest.put(
                        Config.JSON_KEY_CHUNK_BYTES,
                        Util.bytesToHexString( (byte[])obj_chunk_latest.opt(Config.JSON_KEY_CHUNK_BYTES) )
                    );

                    obj_chunk_latest.put( Config.JSON_KEY_EXP_UTC ,now + cache_as_latest_seconds );
                    
                    NetCache.saveNetCache( latest_uri, obj_chunk_latest.toString( )  );
                }
                else{
                     logger.info("saveCache() Ignore cache as newst chunk because exist newer issued chunk for "+latest_uri);
                }
            }
            
        }else{
            //不带版本的数据块直接按cache-as-latest字段的最新版本缓存策略处理
            if(cache_as_latest_seconds>0){
                long exp_utc= Util.getNowTimestamp() + cache_as_latest_seconds;
                obj_decoded_chunk.put( Config.JSON_KEY_EXP_UTC ,exp_utc );
                NetCache.saveNetCache( ap_resp_ppk_uri, obj_decoded_chunk.toString( )  );
            }
        }
      
        //恢复byte[]取值
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_BYTES,chunk_content_bytes);
        
        return true;
      }catch(Exception e){
        logger.info("saveCache() error:"+e.toString());
        return false;
      }
  }
  
  //判断指定URI是否已存在版本更新的缓存
  protected static boolean existNewestCache( String latest_uri , String resource_ver, long iat_utc)
  {
      JSONObject exist_cache =  readCache( latest_uri );
      
      if(exist_cache==null)
          return false;
      
      String exist_resource_uri = exist_cache.optString(Config.JSON_KEY_PPK_URI);
      if(exist_resource_uri == null  )
          return false;
    
      if( exist_resource_uri.equals(latest_uri) ) //是转向记录，需提取实际指向的资源版本地址
          exist_resource_uri = new String( (byte[])exist_cache.opt(Config.JSON_KEY_CHUNK_BYTES) );
      
      String exist_resource_ver =  ODIN.getPPkResourceVer( exist_resource_uri );

      if(exist_resource_ver==null || exist_resource_ver.length()==0 )
          return false;
      
      System.out.println("\n resource_ver="+resource_ver+" , exist_resource_ver="+exist_resource_ver+"\n");
      
      if( Util.isNumeric(exist_resource_ver) && Util.isNumeric(resource_ver) ){
          //都是有效数字时，转换为浮点数比较大小
          BigDecimal exist_resource_num = new BigDecimal(exist_resource_ver);
          BigDecimal resource_num = new BigDecimal(resource_ver);
          
          System.out.println("\n resource_num="+resource_num+" , exist_resource_num="+exist_resource_num+"\n");
          
          return resource_num.compareTo(exist_resource_num)<0 ;
      }else{
          //用生成时间点判断
          long exist_iat_utc = exist_cache.optLong(Config.PTTP_KEY_IAT,0);
          
          System.out.println("\n iat_utc="+iat_utc+" , exist_iat_utc="+exist_iat_utc+"\n");
          return iat_utc < exist_iat_utc ;
      } 
  }
  
  //读取缓存
  protected static JSONObject readCache( String uri )
  {
    JSONObject obj_decoded_chunk=null;
    try{
        String cached_data = NetCache.readNetCache( uri );
        if( cached_data==null || cached_data.length()==0 ){
            //logger.info("readCache() no cache for "+uri );
            return null;
        }
        
        obj_decoded_chunk = new JSONObject( cached_data );
        
        long exp_utc = obj_decoded_chunk.optLong(Config.JSON_KEY_EXP_UTC,0);
        long now = Util.getNowTimestamp();
        //System.out.println("readCache() exp_utc="+exp_utc+ " left:"+(exp_utc-now));
        
        if( exp_utc!=Config.CACHE_NO_EXP_UTC && exp_utc < now){
            //System.out.println("Delete expired cache for "+uri);
            NetCache.deleteNetCache(uri);
            return null;
        }
        
        //将缓存的hex格式字符串转换为byte[]
        byte[] chunk_content_bytes = Util.hexStringToBytes(obj_decoded_chunk.optString(Config.JSON_KEY_CHUNK_BYTES));
        
        if(chunk_content_bytes==null){ //无效的内容数据
            return null;
        }

        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_BYTES,chunk_content_bytes);
        obj_decoded_chunk.put(Config.JSON_KEY_FROM_CACHE,true);
        obj_decoded_chunk.put("debug_cache_file_name", NetCache.getNetCacheFilename(uri));
        
        System.out.println("readCache() OK for "+uri);
        
    }catch(Exception e){
    	logger.info("readCache("+uri+") error:"+e.toString());
    }
    return obj_decoded_chunk;
  }  

  public static JSONObject  fetchAndValidationAP(String uri, String root_odin, JSONObject ap_record,JSONObject  vd_set){
    String ap_url=ap_record.optString("url","");
    
    return fetchAndValidationAP(uri, root_odin, ap_url, vd_set);
  }

  public static JSONObject  fetchAndValidationAP(String uri, String root_odin, String ap_url,JSONObject  vd_set)
  {
    System.out.println("<<< fetchAndValidationAP() uri="+uri+"\n  ap_url="+ap_url);
    
    /*
    JSONObject objInterest=new JSONObject();
    try{
      JSONObject objOption=new JSONObject();
      objOption.put("iss","ppk:12345");
      objOption.put("iat",12345678);
      objOption.put("exp",22345678);
      objOption.put("user_agent",Config.appName+Config.version);
      
      objInterest.put("ver",Config.PTTP_PROTOCOL_VER );
      objInterest.put("hop",6);
      objInterest.put("uri",uri);
      objInterest.put("option",objOption.toString());
    }catch(Exception e){
      logger.info("fetchAndValidationAP("+uri+") meet json exception"+e.toString());
    }
    
    String str_interest=objInterest.toString( );
    */
    String str_interest=uri;//简化的请求格式
    String str_ap_resp_json=null;
    if( ap_url.toLowerCase().startsWith("http:") || ap_url.toLowerCase().startsWith("https:")){
      str_ap_resp_json = APoverHTTP.fetchInterest(ap_url,str_interest);
    }else if( ap_url.toLowerCase().startsWith("ethap:")){
      str_ap_resp_json = APoverETH.fetchInterest(ap_url,str_interest);
    }else if( ap_url.toLowerCase().startsWith(Config.PPK_URI_PREFIX)){
      //对于ppk:  起始的ap的处理，需要先再解析到其指向ap节点，再请求
      //存在循环锁的可能，暂时不支持
      logger.info("fetchAndValidationAP( ) meet unsupported AP URL: "+ap_url);
    }else {
      str_ap_resp_json = Util.fetchUriContent(ap_url);
    }
    
    if(str_ap_resp_json==null || str_ap_resp_json.length()==0 )
        return null;
    
    if(!str_ap_resp_json.startsWith("{")){
      try{
        //兼容不是JSON格式的内容
        String chunk_content=str_ap_resp_json;
        
        JSONObject obj_chunk_metainfo=new JSONObject();
        obj_chunk_metainfo.put(Config.PTTP_KEY_CACHE_AS_LATEST,Config.DEFAULT_CACHE_AS_LATEST);
        obj_chunk_metainfo.put(Config.PTTP_KEY_STATUS_CODE,Config.PTTP_STATUS_CODE_OK);
        obj_chunk_metainfo.put(Config.PTTP_KEY_STATUS_DETAIL,"OK");
        obj_chunk_metainfo.put(Config.PTTP_KEY_CONTENT_TYPE, "text/html"  );
        obj_chunk_metainfo.put(Config.PTTP_KEY_CONTENT_LENGTH, chunk_content.length()  );
        //obj_chunk_metainfo.put("chunk_index", 0 );
        //obj_chunk_metainfo.put("chunk_count", 1 );
        
        JSONObject new_ap_resp=new JSONObject();
        new_ap_resp.put(Config.PTTP_KEY_VER,Config.PTTP_PROTOCOL_VER);
        new_ap_resp.put(Config.PTTP_KEY_SPEC,Config.PTTP_KEY_SPEC_NONE);
        new_ap_resp.put(Config.PTTP_KEY_URI,uri);
        new_ap_resp.put(Config.PTTP_KEY_METAINFO,obj_chunk_metainfo.toString());
        new_ap_resp.put(Config.PTTP_KEY_CONTENT,chunk_content);
        new_ap_resp.put(Config.PTTP_KEY_SIGNATURE,"");
        
        str_ap_resp_json = new_ap_resp.toString();
      }catch(Exception e){
        logger.error("fetchAndValidationAP( ) error: "+e.toString());
      }
    }
    
    return parseRespOfPTTP(ap_url,str_ap_resp_json,uri,vd_set);
  }

  //解析AP按PTTP协议所应答的数据包(JSON格式字符串)
  protected static JSONObject parseRespOfPTTP(String ap_url,String str_ap_data_json,String str_req_uri, JSONObject  vd_set){
    JSONObject obj_decoded_chunk = null;
    String   str_spec = null;
    String   str_uri = null;
    String   str_metainfo_json = null;
    String   str_content = null;
    String   str_signature = null;
    
    //从应答JSON字符串解析出所需数据描述、正文、签名等字段
    try{
      JSONObject obj_ap_data = new JSONObject(str_ap_data_json);
      
      //兼容v1旧版本的处理
      if(obj_ap_data.has("data")){
           return parseRespOfPTTPv1(ap_url,str_ap_data_json,str_req_uri, vd_set);
      }
      
      
      str_uri = obj_ap_data.getString(Config.PTTP_KEY_URI);
      
      //检查应答结果的标识是否与指定的请求URI匹配
      if(str_req_uri!=null){
          if(str_uri==null 
            || !(
                    ( str_req_uri.endsWith( Config.PPK_URI_RESOURCE_MARK ) &&  str_uri.startsWith(str_req_uri)  )
                  || str_uri.equals(str_req_uri)    
                )
            ){
            logger.info("parseRespOfPTTP("+ap_url+") meet mismatched data URI: "+str_uri);
            
            return genLocalRespChunk(
                        str_uri,
                        Config.PTTP_STATUS_CODE_LOCAL_ERROR,
                        "text/html",
                        "Meet mismatched data URI: "+ str_uri  +" \nRequest URI: "+str_req_uri,
                        Config.PTTP_VALIDATION_ERROR,
                        Util.getNowTimestamp( ),
                        Config.IGNORE_CACHE_AS_LATEST,
                        ap_url,
                        str_ap_data_json
                    );
          }
      }
      
      str_spec = obj_ap_data.optString(Config.PTTP_KEY_SPEC,Config.PTTP_KEY_SPEC_NONE);
      str_content = obj_ap_data.optString(Config.PTTP_KEY_CONTENT,"");
      str_signature = obj_ap_data.optString(Config.PTTP_KEY_SIGNATURE,"");
      
      JSONObject obj_chunk_metainfo = null;
      str_metainfo_json = obj_ap_data.optString(Config.PTTP_KEY_METAINFO,null);
      
      //System.out.println("\nPPkURI:parseRespOfPTTP()\nstr_uri="+str_uri);
      //System.out.println("str_spec="+str_spec);
      //System.out.println("str_metainfo_json="+str_metainfo_json);

      if(str_metainfo_json!=null){
        obj_chunk_metainfo = new JSONObject(str_metainfo_json);
      }else{ //兼容data取值不是字符串而是JSONObject的非标准形式
        obj_chunk_metainfo = obj_ap_data.getJSONObject(Config.PTTP_KEY_METAINFO);
        str_metainfo_json = obj_chunk_metainfo.toString();
      }
      
      String chunk_content_encoding = (obj_chunk_metainfo==null) ? "":obj_chunk_metainfo.optString(Config.PTTP_KEY_CONTENT_ENCODING,"").toLowerCase();
      String chunk_content_type = (obj_chunk_metainfo==null) ? "text/html":obj_chunk_metainfo.optString(Config.PTTP_KEY_CONTENT_TYPE,"");
      byte[] chunk_content_bytes = null;
      
      int pttpStatusCode=obj_chunk_metainfo.getInt(Config.PTTP_KEY_STATUS_CODE);
      System.out.println("PttpStatusCode="+pttpStatusCode);
      
      obj_decoded_chunk = new JSONObject();
      obj_decoded_chunk.put(Config.JSON_KEY_ORIGINAL_RESP, str_ap_data_json );
      obj_decoded_chunk.put(Config.PTTP_KEY_SPEC, str_spec );
      obj_decoded_chunk.put(Config.PTTP_KEY_STATUS_CODE, pttpStatusCode );
      obj_decoded_chunk.put(Config.PTTP_KEY_IAT, obj_chunk_metainfo.optInt(Config.PTTP_KEY_IAT,0) );
      obj_decoded_chunk.put(Config.PTTP_KEY_CACHE_AS_LATEST, 
                              obj_chunk_metainfo.optString(Config.PTTP_KEY_CACHE_AS_LATEST,"")  );
      
      
      if (pttpStatusCode == Config.PTTP_STATUS_CODE_OK) {
    	if(str_content.length()==0) {
    		chunk_content_bytes=str_content.getBytes();
    	}else {
	        if("base64".equals(chunk_content_encoding)){
	          chunk_content_bytes=Base64.getDecoder().decode(str_content);
	        }else if("hex".equals(chunk_content_encoding)){
	          chunk_content_bytes=Util.hexStringToBytes(str_content);
	        }else{
	          chunk_content_bytes=str_content.getBytes();
	        }
    	}
      }else{
        String str_status_detail=obj_chunk_metainfo.optString(Config.PTTP_KEY_STATUS_DETAIL,"");
        
        if(str_content.length()==0)
            chunk_content_bytes = ("PTTP status_code : "+pttpStatusCode + " " + str_status_detail).getBytes(); 
        else
            chunk_content_bytes = str_content.getBytes(); 
      }
      
      obj_decoded_chunk.put(Config.JSON_KEY_PPK_URI,str_uri);
      obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_TYPE,chunk_content_type);
      obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_BYTES,chunk_content_bytes);
      obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_LENGTH,chunk_content_bytes.length);
      obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_URL,ap_url);
       
      obj_decoded_chunk.put(Config.PTTP_KEY_SIGNATURE, str_signature );
    }catch(Exception e){
      logger.info("parseRespOfPTTP("+ap_url+") meet error: "+e.toString());
      return null;
    }
    
    /*
    //for debug
    try{
        obj_decoded_chunk.put(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_IGNORED);
    }catch (Exception e) {
        e.printStackTrace();
    }

    return obj_decoded_chunk;
    */
    
    try{
        String vd_set_pubkey="";
        
        if(vd_set!=null){
            vd_set_pubkey = vd_set.optString(Config.ODIN_SET_VD_PUBKEY,"");
            //String vd_set_cert_uri=vd_set.optString(Config.ODIN_SET_VD_CERT_URI,"");
            String vd_set_type=vd_set.optString(Config.ODIN_SET_VD_TYPE,Config.ODIN_SET_VD_ENCODE_TYPE_PEM);
            
            if( Util.isURI(vd_set_pubkey) ){
                //如果公钥字段取值还是URI链接，需尝试动态获取和更新公钥
                logger.info("Auto fetch vd_set_pubkey from URI:"+vd_set_pubkey);

                String tmp_str=Util.fetchUriContent(vd_set_pubkey);
                if(tmp_str!=null && tmp_str.length()>0){
                    vd_set_pubkey = tmp_str;
                    vd_set.put(Config.ODIN_SET_VD_PUBKEY, vd_set_pubkey);
                    
                    //待完善，将动态获得的公钥自动存入数据库
                    //OdinInfo odinInfo=ODIN.getOdinInfo(root_odin);
                    //JSONObject  new_vd_set=odinInfo.odinSet.getJSONObject("vd_set");
                    //new_vd_set.put(Config.ODIN_SET_VD_PUBKEY,vd_set_pubkey);
                    //odinInfo.odinSet.put("vd_set",new_vd_set);
                    
                    //Database db = Database.getInstance();
                    //PreparedStatement ps;
                    //ps = db.connection.prepareStatement("UPDATE odins SET odin_set=? WHERE full_odin=?;");

                    //ps.setString(1, odinInfo.odinSet.toString());
                    //ps.setString(2, odinInfo.fullOdin);
                    //ps.execute(); 
                }
            }
        }
    
        obj_decoded_chunk.put(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_ERROR);

        if(vd_set_pubkey.trim().length()==0){
            //在未设置有效签名验证公钥时，忽略检查签名
            logger.info("No valid pubkey.Ignored to verify the chunk.");
            obj_decoded_chunk.put(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_IGNORED);
        }else{
            //检查签名
            if( str_spec.equals(Config.PTTP_KEY_SPEC_PAST+Config.PTTP_KEY_SPEC_PAST_HEADER_V1_PUBLIC) ){
              byte[] key_bytes =  RSACoder.decryptBASE64( RSACoder.parseValidPubKey(  vd_set_pubkey) )  ;
              
              String payload = Config.PTTP_SIGN_MARK_DATA+str_uri+str_metainfo_json+str_content;

              byte[] m2 = PasetoUtil.pae(
                            Config.PTTP_KEY_SPEC_PAST_HEADER_V1_PUBLIC.getBytes(Config.PPK_TEXT_CHARSET), 
                            payload.getBytes(Config.PPK_TEXT_CHARSET),
                            "".getBytes(Config.PPK_TEXT_CHARSET)
                          );
              
              PasetoPublic.verify(
                    key_bytes, 
                    m2, 
                    PasetoUtil.decodeFromString(str_signature)
              );
              
              obj_decoded_chunk.put(Config.JSON_KEY_PPK_VALIDATION,Config.PTTP_VALIDATION_OK);
            }else{
              logger.info("Meet unsupported or invalid pttp spec("+str_spec+").");
            }
        }
    }catch(Exception e){
        logger.info("parseRespOfPTTP("+ap_url+") meet invalid ppk signature: "+ e.toString());
    }
    
    return obj_decoded_chunk;
  }
  
  //本地生成数据块（如本地解析根标识或者处理出错时的应答）
  protected static JSONObject genLocalRespChunk(
        String str_uri,
        int status_code,
        String chunk_type,
        String chunk_content,
        int validate_code,
        long iat_utc,
        String cache_as_latest,
        String original_ap_url,
        String str_original_ap_data_json
  ){
    try{
        str_uri = ODIN.formatPPkURI(str_uri);
        
        JSONObject obj_decoded_chunk = new JSONObject();
        
        byte[] tmp_chunk_bytes = chunk_content==null ? "".getBytes() : chunk_content.getBytes();
        
        obj_decoded_chunk.put(Config.JSON_KEY_PPK_URI,str_uri);

        obj_decoded_chunk.put(Config.PTTP_KEY_SPEC, Config.PTTP_KEY_SPEC_NONE );
        obj_decoded_chunk.put(Config.PTTP_KEY_STATUS_CODE, status_code ); //表示PPk自定义错误代码
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_TYPE,chunk_type);
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_BYTES,tmp_chunk_bytes);
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_LENGTH,tmp_chunk_bytes.length);
        obj_decoded_chunk.put(Config.PTTP_KEY_IAT, iat_utc );
        obj_decoded_chunk.put(Config.PTTP_KEY_CACHE_AS_LATEST, cache_as_latest);

        obj_decoded_chunk.put(Config.JSON_KEY_PPK_VALIDATION,validate_code);
        
        obj_decoded_chunk.put(Config.JSON_KEY_CHUNK_URL,original_ap_url);
        obj_decoded_chunk.put(Config.JSON_KEY_ORIGINAL_RESP, str_original_ap_data_json );
        
        return obj_decoded_chunk;
    }catch(Exception e){
        logger.info("genLocalRespChunk() meet error: "+e.toString());
        return null;
    }
  }
  
  //解析处理v1旧版本的数据报文
  protected static JSONObject parseRespOfPTTPv1(String ap_url,String str_ap_data_json,String str_req_uri, JSONObject  vd_set){
    String   str_original_data_json = null;
    
    //从应答JSON字符串解析出所需数据正文、签名等字段
    try{
      JSONObject obj_ap_data = new JSONObject(str_ap_data_json);
      JSONObject obj_data = null;
      str_original_data_json = obj_ap_data.optString("data",null);
      System.out.println("PPkURI:parseRespOfPTTPv1() str_original_data_json="+str_original_data_json);
      if(str_original_data_json!=null){
        obj_data = new JSONObject(str_original_data_json);
      }else{ //兼容data取值不是字符串而是JSONObject的非标准形式
        obj_data = obj_ap_data.getJSONObject("data");
        str_original_data_json = obj_data.toString();
      }
      JSONObject obj_chunk_metainfo=obj_data.optJSONObject("metainfo");
      
      String chunk_content_encoding = (obj_chunk_metainfo==null) ? "":obj_chunk_metainfo.optString("content_encoding","").toLowerCase();
      String chunk_content_type = (obj_chunk_metainfo==null) ? "text/html":obj_chunk_metainfo.optString("content_type","");
      
      int pttpStatusCode=obj_data.getInt("status_code");
      System.out.println("parseRespOfPTTPv1() pttpStatusCode="+pttpStatusCode);

      String str_content=obj_data.optString("content","");;
      if (pttpStatusCode == Config.PTTP_STATUS_CODE_OK ) {
        if("base64".equals(chunk_content_encoding)){
            str_content= new String(Base64.getDecoder().decode(str_content));
        }else if("hex".equals(chunk_content_encoding)){
        	str_content=new String(Util.hexStringToBytes(obj_data.getString("content")));
        }
      }else{
        String str_status_detail=obj_data.optString("status_detail","");
        if(str_content.length()==0)
          str_content="PTTP status_code : "+pttpStatusCode + " " + str_status_detail ;
      }
      
      return genLocalRespChunk(
    			  obj_data.getString("uri"),
                  pttpStatusCode,
                  chunk_content_type,
                  str_content,
                  Config.PTTP_VALIDATION_IGNORED,
                  Util.getNowTimestamp( ),
                  Config.IGNORE_CACHE_AS_LATEST,
                  ap_url,
                  str_ap_data_json
              );
              
    }catch(Exception e){
      logger.info("parseRespOfPTTPv1("+ap_url+") error: "+e.toString());
      return null;
    }
    
  }
  
}
