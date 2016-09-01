import java.nio.ByteBuffer;

//比特币协议定义的可变长度整型VARINT格式处理
public class BitcoinVarint {
    private int mIntVal;
    private int mIntSize;
    
    public BitcoinVarint(int val,int size){
      mIntVal=val;
      mIntSize=size;
    }
    
    public int intValue(){
      return mIntVal;
    }
    
    public int size(){
      return mIntSize;
    }
    
    //32位无符号整数转换成VARINT格式的字节数组(低位在前，高位在后)
    public static byte[] toBytes(int val){
      byte[] result_array=null;
      
      if(val<0xFD){
        result_array=new byte[1];
        result_array[0] = (byte)val;
      }else if(val<0xFFFF ){ 
        result_array=new byte[3];
        result_array[0] = (byte)0xFD;
        result_array[1] = (byte)(val%256);
        result_array[2] = (byte)(val/256);
      }else if(val<0xFFFFFFF ){
        result_array=new byte[5];
        result_array[0] = (byte)0xFE;
        result_array[1] = (byte)((val%65536)%256);
        result_array[2] = (byte)((val%65536)/256);
        result_array[3] = (byte)((val/65536)%256);
        result_array[4] = (byte)((val/65536)/256);
      }
      
      return result_array;
  }
  
  //从指定直接缓存的指定位置读取Varint格式数据
  public static BitcoinVarint getFromBuffer(ByteBuffer byteBuffer,int from){
    BitcoinVarint result_val=null;
    int tmp_byte=getUnsignedByteVal(byteBuffer.get(from));
    if(tmp_byte<0xFD){
      result_val=new BitcoinVarint(tmp_byte,1);
    }else if(tmp_byte==0xFD){
      result_val=new BitcoinVarint( 
           getUnsignedByteVal(byteBuffer.get(from+1))
          +getUnsignedByteVal(byteBuffer.get(from+2))*256,
          3);
    }else if(tmp_byte==0xFE){
      result_val=new BitcoinVarint(
           getUnsignedByteVal(byteBuffer.get(from+1))
          +getUnsignedByteVal(byteBuffer.get(from+2))*256
          +getUnsignedByteVal(byteBuffer.get(from+3))*65536
          +getUnsignedByteVal(byteBuffer.get(from+4))*65536*256,
          5);
    }

    return result_val;
  }
  
  public static int getUnsignedByteVal(byte a){
    int i = a;    
    i = a&0xff;  
    return i;  
  }
}