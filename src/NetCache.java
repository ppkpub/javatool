//网络内容本地缓存管理
public class NetCache {
  //写入缓存，待完善
  //改用org.apache.commons.io.FileUtils
  public static boolean saveNetCache( String uri, String data  )
  {
    String cache_filename = getNetCacheFilename(uri);
    
    if( cache_filename == null ){ 
        return false;
    }
    
    System.out.println("NetCache.saveNetCache() :"+cache_filename);
    Util.exportTextToFile(data,cache_filename);
    
    return true;
  }
  
  //读取缓存
  public static String readNetCache( String uri )
  {
    String cache_filename = getNetCacheFilename(uri);
    //System.out.println("Util.readNetCache() cache_filename = "+cache_filename);
    String str_cached_data = Util.readTextFile(cache_filename);
    
    /*
    if(str_cached_data==null){   
        System.out.println("Util.readNetCache() failed to read cache for "+uri);
    }else{
        System.out.println("Util.readNetCache() matched cache for "+uri);
    }
    */
    
    return str_cached_data;
  }
  
  //删除缓存
  public static void deleteNetCache( String uri )
  {
    String cache_filename = getNetCacheFilename(uri);
    System.out.println("Util.deleteNetCache() cache_filename = "+cache_filename);

    Util.deleteDir(cache_filename,true);
  }
  
  public static String getNetCacheFilename( String uri )
  {
    if(uri==null)
        return null;

    String format_safe_filename = java.net.URLEncoder.encode(uri.replaceFirst(":","/"))
                                 .replace("%2F","/").replace("*","#").replace("..","__");
    return  Config.cacheDirPrefix + format_safe_filename;
  }
  
}