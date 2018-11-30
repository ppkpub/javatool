/*
 * Name:BrowserPageNode.java
 * Writer:bitsjx
 * Date:2018-11-26
 * Time:00:20
 * Function:implement a Node of LinkList
 * */
public class BrowserPageNode {
	//页面的名称
	private String pagename="";
	//url为网址实例 
	private String url="";
	//next为后一个节点
	private BrowserPageNode next=null;
	//pre为前一个节点
	private BrowserPageNode pre=null;
	
	//get、set方法
	public String getPagename() {
		return pagename;
	}
	public void setPagename(String pagename) {
		this.pagename = pagename;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public BrowserPageNode getNext() {
		return next;
	}
	public void setNext(BrowserPageNode next) {
		this.next = next;
	}
	public BrowserPageNode getPre() {
		return pre;
	}
	public void setPre(BrowserPageNode pre) {
		this.pre = pre;
	}
	
	//重写默认构造函数
	public BrowserPageNode()
	{
    //System.out.println("BrowserPageNode blank");
		this.pagename="";
		this.url="";
		this.next=null;
		this.pre=null;
	}
	//自定义构造函数
	public BrowserPageNode(String pagename,String urladdress)
	{
    //System.out.println("BrowserPageNode:"+pagename+","+urladdress);
		this.pagename=pagename;
		this.url=urladdress;
		this.next=null;
		this.pre=null;
	}
}
