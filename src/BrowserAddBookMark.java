/*
 * Name:BrowserAddBookMark.java
 * Writer:bitsjx
 * Date:2018-11-26
 * Time:00:20
 * Function:Use class MainUI to implement a BrowserAddBookMark GUI
 * */
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
public class BrowserAddBookMark extends JFrame implements ActionListener {
	//定义组件
	JPanel contentpane;
	//添加文本区域的面板
	JPanel textpanel=new JPanel();
	//添加按钮的面板
	JPanel buttonpanel=new JPanel();
	//名称、地址
	JLabel namelabel=new JLabel("名称");
	JLabel addresslabel=new JLabel("地址");
	//显示名称和地址的文本框
	JTextField nametext=new JTextField(20);
	JTextField addresstext=new JTextField(20);
	//确定、取消按钮
	JButton OKbuuton=new JButton("确定");
	JButton Cancelbuuton=new JButton("取消");
	//isExist用于判断是否已经存在书签项
	boolean isExist=false;
	public BrowserAddBookMark(String title,String urladdress,boolean isExist)
	{
		//设置窗口属性
		setLocationRelativeTo(null);
		setResizable(false);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		this.enableEvents(AWTEvent.WINDOW_EVENT_MASK);
		this.setSize(new Dimension(300,150));
		this.setTitle(" 收藏夹");
		this.isExist=isExist;
		//设置窗体属性
		contentpane=(JPanel)this.getContentPane();
		contentpane.setLayout(new BorderLayout());
		textpanel.setLayout(new FlowLayout());
		textpanel.add(namelabel);
		textpanel.add(nametext);
		nametext.setEnabled(true);
		nametext.setText(title);
		nametext.setFocusable(true);
		textpanel.add(addresslabel);
		textpanel.add(addresstext);
		addresstext.setEditable(true);
		addresstext.setText(urladdress);
		buttonpanel.setLayout(new FlowLayout());
		buttonpanel.add(OKbuuton);
		OKbuuton.setFocusable(false);
		buttonpanel.add(Cancelbuuton);
		Cancelbuuton.setFocusable(false);
		contentpane.add(textpanel,BorderLayout.CENTER);
		contentpane.add(buttonpanel,BorderLayout.SOUTH);
		OKbuuton.addActionListener(this);
		Cancelbuuton.addActionListener(this);
	}
	//响应按钮事件
	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource()==Cancelbuuton)
		{
			dispose();
		}
		if(e.getSource()==OKbuuton)
		{
			//如果存在了书签项
			if(this.isExist==true)
			{
				if (JOptionPane.showConfirmDialog(BrowserAddBookMark.this, "项目已存在，是否覆盖?",
						"提示:", JOptionPane.OK_CANCEL_OPTION)==0) {
				}
			}
			dispose();
		}
	}
}