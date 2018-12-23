import java.math.BigInteger;

public class UnspentOutput {
    public BigInteger amt_satoshi; //以satoshi为单位
    public String txid;
    public Integer vout;
    //public String type;
    //public Integer confirmations;
    
    public String scriptPubKeyHex;
    
    public String toString(){
        return "UnspentOutput: txid="+txid+" , vout="+vout+",amt_satoshi="+amt_satoshi+" , scriptPubKeyHex="+scriptPubKeyHex;
    }
}
