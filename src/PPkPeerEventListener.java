import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.GetDataMessage;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.listeners.AbstractPeerEventListener;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.PeerAddress;
import java.util.Set;

public class PPkPeerEventListener extends AbstractPeerEventListener {
    Logger logger = LoggerFactory.getLogger(PPkPeerEventListener.class);

  @Override
  public List<Message> getData(Peer peer, GetDataMessage message) {
    return null;
  }

  @Override
  public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft){
    logger.info("BlockHeader left: "+blocksLeft);
    /*
    //this doesn't work
    Blocks blocks = Blocks.getInstance();
    try {
      blocks.importBlock(block, blocks.blockStore.get(block.getHash()).getHeight());
    } catch (BlockStoreException e) {
    }
    */
  }

  @Override
  public void onChainDownloadStarted(Peer peer, int blocksLeft) {
    logger.info("Chain download started: "+blocksLeft);
  }

  @Override
  public void onPeerConnected(Peer peer, int peerCount) {
    logger.info("Peer connected: "+peerCount);
  }

  @Override
  public void onPeerDisconnected(Peer peer, int peerCount) {
    logger.info("Peer disconnected: "+peerCount);
  }

  @Override
  public Message onPreMessageReceived(Peer peer, Message message) {
    return null;
  }

  @Override
  public void onTransaction(Peer peer, Transaction tx) {
    logger.info("Got transaction");    
  }
    
    @Override
  public void onPeersDiscovered(Set<PeerAddress> peerAddresses){
        logger.info("Peers discovered");    
    }
  
}