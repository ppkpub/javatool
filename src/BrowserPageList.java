/*
 * Name:BrowserPageList.java
 * Writer:bitsjx
 * Date:2018-11-26
 * Time:00:26
 * Function:Construct a LinkList to save History and BookMarks Nodes
 * */
public class BrowserPageList {
	//头节点
	private BrowserPageNode head;
	//自由节点
	private BrowserPageNode link;
	//记录当前的节点所在位置
	private BrowserPageNode linkpointer;
	public BrowserPageList()
	{
		head=new BrowserPageNode();
		link=head;
		linkpointer=head;
	}
	//添加新的页面到链表
	public void addURL(String pagename,String urladdress)
	{
		BrowserPageNode node=new BrowserPageNode(pagename,urladdress);
		link=head;
		while(link.getNext()!=null)
		{
			link=link.getNext();
		}
		link.setNext(node);
		node.setPre(link);
		node.setNext(null);
		linkpointer=node;
	}
	//获得最新的链表头
	public BrowserPageNode getLastPageNode()
	{
		return this.head;
	}
	
  //获得当前页面
	public BrowserPageNode getCurrentPageNode()
	{
		return this.linkpointer;
	}
  
	//查找某个pagename对应的URL
	public String getURL(String pagename)
	{
		link=head;
		String url="";
		while(!((link.getNext()).getPagename()).equalsIgnoreCase(pagename))
		{
			link=link.getNext();
		}
		url=(link.getNext()).getUrl();
		linkpointer=link;
		return url;
	}
	
	//获取某个URL对应的pagename
	public String getPageName(String urladdress)
	{
		link=head;
		String pagename="";
		while(!(link.getNext().getUrl()).equalsIgnoreCase(urladdress))
		{
			link=link.getNext();
		}
		pagename=link.getNext().getPagename();
		linkpointer=link;
		return pagename;
	}
	//获取当前page的前一个pageurl
	public String getPrePageURL( )
	{
		link=linkpointer.getPre();
		linkpointer=link;
		return link.getUrl();
	}
	//获取当前page的下一个pageurl
	public String getNextPageURL()
	{
		link=linkpointer.getNext();
		linkpointer=link;
		return link.getUrl();
	}
	//查找某个pageurl地址是否存在
	public boolean isPageUrlExist(String pageurl)
	{
		link=this.head.getNext();
		//标记是否找到
		boolean isfind=false;
		String tmpUrl="";
		while(link!=null)
		{
			tmpUrl=link.getUrl();
			if(tmpUrl.equalsIgnoreCase(pageurl))
			{
				isfind=true;
				break;
			}
			link=link.getNext();
		}
		System.out.println("查找本页结果:"+isfind);
		return isfind;
	}
	//判断前一个page是否存在
	public boolean isPrePageExist( )
	{
		boolean isfind=false;
		link=head;
		link=linkpointer.getPre();
		if(link!=null&&link!=head)
		{
			isfind=true;
		}
		System.out.println("查找前页结果:"+isfind);
		return isfind;
	}
	//判断后一个page是否存在
	public boolean isNextPageExist( )
	{
		boolean isfind=false;
		link=head;
		link=linkpointer.getNext();
		if(link!=null)
		{
			isfind=true;
		}
		System.out.println("查找后页结果:"+isfind);
		return isfind;
	}
}
