import java.math.BigInteger;
import java.util.List;

public class UnspentOutput {
    public Double amount;
    public String txid;
    public Integer vout;
    //public String type;
    //public Integer confirmations;
    
    public String scriptPubKeyHex;
    //public String scriptPubKeyAsm;
    /*
    public ScriptPubKey scriptPubKey;
    
    public class ScriptPubKey {
      public String asm;
      public String hex;
    }
    */
}
/*
OLD:
{"txid":"a59c72bade2aa594051c5b4f032996979b2dba70c564b1275e813799c44d9d81",
"vout":0,
"amount":0.000078,"type":"multisig","reqSigs":1,
"addresses":["1GV6PLMntWkpGN3qvF4VwgnjAP2octu763"],
"scriptPubKey":{"asm":"1 028dccef4b3e3a36874859f472de863919a5bc36acee1aa4f8d423459b54ad3654 20434e5452505254590000000a0000000000000000000000000098a04400000000 2 OP_CHECKMULTISIG","hex":"5121028dccef4b3e3a36874859f472de863919a5bc36acee1aa4f8d423459b54ad36542120434e5452505254590000000a0000000000000000000000000098a0440000000052ae"},

NOW: from blockchain.info
{
            "tx_hash":"d004ff067bdfc76560a9b9177b443057d8b97c76621be54d87802eed087219ea",
            "tx_hash_big_endian":"ea197208ed2e80874de51b62767cb9d85730447b17b9a96065c7df7b06ff04d0",
            "tx_index":76649992,
            "tx_output_n": 0,
            "script":"76a914a9da0766d0f0d7dc35f54e8c8c14a26480a7798e88ac",
            "value": 7800,
            "value_hex": "1e78",
            "confirmations":76959
        },
*/