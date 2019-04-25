import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.List;

public class UnspentList {
    public List<UnspentOutput> unspents;
    public BigInteger sum_satoshi; //合计金额，以satoshi为单位
    public int tx_num;
    public int tx_total_num;
 
    public UnspentList(List<UnspentOutput> utxos,int num,long sum,int total_num ){
        this.unspents=utxos ;
        this.sum_satoshi= BigInteger.valueOf( sum ); 
        this.tx_num=num ;
        this.tx_total_num=total_num ;
    }
    
    
}
