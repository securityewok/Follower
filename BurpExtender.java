package burp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

public class BurpExtender extends AbstractTableModel implements IBurpExtender, IHttpListener, 
        IContextMenuFactory, ITab, IMessageEditorController, IScannerCheck
{
    private IBurpExtenderCallbacks callbacks;
    private JSplitPane splitPane;
    private IMessageEditor requestOneViewer;
    private IMessageEditor requestTwoViewer;
    private IMessageEditor responseViewer;
    private ARequestsIds requestsIds = new ARequestsIds();  
    private IHttpRequestResponse currentlyDisplayedItem;
    private static IExtensionHelpers helpers;
    private final List<LogEntry> log = new ArrayList<LogEntry>();
    private final String f0lwr = "f0lwr";

    
    //
    // implement IBurpExtender
    //
    
    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks)
    {
        // Keep a reference to our callbacks object
        this.callbacks = callbacks;
        
        // Set our extension name
        callbacks.setExtensionName("Follower");
        
        // register ourselves as an HTTP listener
        callbacks.registerHttpListener(this); 
        
        // register ourself to a Menu listener
        callbacks.registerContextMenuFactory(this);
        
        // register ourself to a Scanner
        callbacks.registerScannerCheck(this);
        
        // get Helpers
        helpers = callbacks.getHelpers();
        
        SwingUtilities.invokeLater(new Runnable() 
        {
            @Override
            public void run()
            {
                // main split pane
                splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
                        
                // table of log entries
                Table logTable = new Table(BurpExtender.this);
                JScrollPane scrollPane = new JScrollPane(logTable);
                splitPane.setLeftComponent(scrollPane);
                logTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
                logTable.getColumnModel().getColumn(1).setPreferredWidth(150);
                logTable.getColumnModel().getColumn(2).setPreferredWidth(1000);
                // tabs with request/response viewers
                JTabbedPane tabs = new JTabbedPane();
                requestOneViewer = callbacks.createMessageEditor(BurpExtender.this, false);
                requestTwoViewer = callbacks.createMessageEditor(BurpExtender.this, false);
                responseViewer = callbacks.createMessageEditor(BurpExtender.this, false);
                tabs.addTab("Request1", requestOneViewer.getComponent());
                tabs.addTab("Request2", requestTwoViewer.getComponent());
                tabs.addTab("Reflection", responseViewer.getComponent());
                splitPane.setRightComponent(tabs);

                // customize our UI components
                callbacks.customizeUiComponent(splitPane);
                callbacks.customizeUiComponent(logTable);
                callbacks.customizeUiComponent(scrollPane);
                callbacks.customizeUiComponent(tabs);
                
                // add the custom tab to Burp's UI
                callbacks.addSuiteTab(BurpExtender.this);
            }
        });
    }
    
    @Override
    public String getTabCaption()
    {
        return "Follower";
    }

    @Override
    public Component getUiComponent()
    {
        return splitPane;
    }
    
    @Override
    public byte[] getRequest()
    {
        return currentlyDisplayedItem.getRequest();
    }
    
    @Override
    public IHttpService getHttpService()
    {
        return currentlyDisplayedItem.getHttpService();
    }
    
    @Override
    public byte[] getResponse()
    {
        return currentlyDisplayedItem.getResponse();
    }
    
    @Override
    public int getRowCount()
    {
        return log.size();
    }

    @Override
    public int getColumnCount()
    {
        return 3;
    }

    @Override
    public String getColumnName(int columnIndex)
    {
        switch (columnIndex)
        {
	    case 0:
		return "ID";
            case 1:
                return "Tool";
            case 2:
                return "URL";
            default:
                return "";
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        LogEntry logEntry = log.get(rowIndex);

        switch (columnIndex)
        {
            case 0:
        	return rowIndex;
            case 1:
                return callbacks.getToolName(logEntry.tool);
            case 2:
                return logEntry.url.toString();
            default:
                return "";
        }
    }
    
    //Extension tab table class
    private class Table extends JTable
    {
        public Table(TableModel tableModel)
        {
            super(tableModel);
        }
        
        @Override
        public void changeSelection(int row, int col, boolean toggle, boolean extend)
        {
            // show the log entry for the selected row
            LogEntry logEntry = log.get(row);
            requestOneViewer.setMessage(logEntry.requestResponseTwo.getRequest(), true);
            requestTwoViewer.setMessage(logEntry.requestResponse.getRequest(), true);
            responseViewer.setMessage(logEntry.requestResponse.getResponse(), false);
            currentlyDisplayedItem = logEntry.requestResponse;
            
            super.changeSelection(row, col, toggle, extend);
        }        
    }
    
    //Create menu element
    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation)
    {
    	List<JMenuItem> menuOptions = new ArrayList<>();
    	JMenuItem item = new JMenuItem("Generate Follower");
    	item.addActionListener(new AOrderListener());
    	menuOptions.add(item);
    	return menuOptions;
    }
    
    //Listener to menu
    class AOrderListener implements ActionListener, ClipboardOwner
    {
    	public void actionPerformed(ActionEvent e) {
    		AUtils autil = new AUtils();
    		String uid = autil.generateUid();
    		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(uid), this);
    		ARequestsIds requestsIds = new ARequestsIds();
    		requestsIds.addUid(uid);
    	}
    	
    	@Override
    	public void lostOwnership(Clipboard aClipboard, Transferable aContents) {
    	}
    }
    
    
    
    //Log entries for extension tab
    private static class LogEntry
    {
        final int tool;
        final IHttpRequestResponsePersisted requestResponse;
        final IHttpRequestResponse requestResponseTwo;
        final URL url;

        LogEntry(int tool, IHttpRequestResponsePersisted requestResponse, URL url, IHttpRequestResponse requestResponseTwo)
        {
            this.tool = tool;
            this.requestResponse = requestResponse;
            this.requestResponseTwo = requestResponseTwo;
            this.url = url;
        }
    }
     

    //Process HTTP messages
    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo)
    {
    	if(messageIsRequest) {
    		if (callbacks.isInScope(helpers.analyzeRequest(messageInfo).getUrl())) {
				String request = helpers.bytesToString(messageInfo.getRequest());
				if(request.indexOf("f0lwr") != -1) {
					requestsIds.addRequest(callbacks.saveBuffersToTempFiles(messageInfo));
				}
			}
    	}
    	if(!messageIsRequest) {
    		if (callbacks.isInScope(helpers.analyzeRequest(messageInfo).getUrl())) {
	    		String response = helpers.bytesToString(messageInfo.getResponse());
	    		if(response.indexOf("f0lwr") != -1) {
	    			Pattern pattern = Pattern.compile(f0lwr+"[a-z,0-9]{8}");
	    			Matcher matcher = pattern.matcher(response);
	    			if(matcher.find()) {
	    				if(requestsIds.findUId(matcher.group()) == "Match") { 
	    					IHttpRequestResponse request = requestsIds.findRequest(matcher.group());
	        				// create a new log entry with the message details	        			
	        	            synchronized(log)
	        	            {
	        	                int row = log.size();
	        	                log.add(new LogEntry(toolFlag, callbacks.saveBuffersToTempFiles(messageInfo), 
	        	                        helpers.analyzeRequest(messageInfo).getUrl(), request));
	        	                fireTableRowsInserted(row, row);
	        	            }
	    				}
	    			}
	    		}
    		}	
    	}
	}
    
    //Scanner methods
    @Override
    public List<IScanIssue> doActiveScan(IHttpRequestResponse baseRequestResponse, IScannerInsertionPoint insertionPoint) {
    	AUtils autils = new AUtils();
    	String uId = autils.generateUid();
    	ARequestsIds requestsIds = new ARequestsIds();
		requestsIds.addUid(uId);
        byte[] checkRequest = insertionPoint.buildRequest(uId.getBytes());
        callbacks.makeHttpRequest(baseRequestResponse.getHttpService(), checkRequest);
    	return null;
    }
    
    @Override
    public List<IScanIssue> doPassiveScan(IHttpRequestResponse baseRequestResponse) {
    	return null;
    }
    
    @Override
    public int consolidateDuplicateIssues(IScanIssue existingIssue, IScanIssue newIssue) {
    	return 0;
    }
       
    
    //Object with Requests and generated Ids
    static class ARequestsIds {

    	static List<String> uIds = new ArrayList<String>();
    	static List<IHttpRequestResponse> requests = new ArrayList<IHttpRequestResponse>();
    	
    	public void addUid(String uId) {
    		uIds.add(uId);
    	}
    		
    	public void addRequest(IHttpRequestResponse messageInfo) {
    		requests.add(messageInfo);
    	}
    	
    	public String findUId(String uId) {
    		if(uIds.contains(uId)) {
    			return "Match";
    		} else {
    			return null;
    		}
    	}
    	
    	public IHttpRequestResponse findRequest(String uId) {
    		for (int i=0; i < requests.size(); i++) {
    			String request = helpers.bytesToString(requests.get(i).getRequest());
    			if(request.indexOf(uId) != -1) {
    				return requests.get(i);
    			} 
    		} return null;
    	}
    }
    
    
    //Helpful utils
    class AUtils {
    	public String generateUid() {
            long t1 = Instant.now().getEpochSecond();
            String seed = Long.toString(t1);
    		try {
    			MessageDigest md = MessageDigest.getInstance("SHA-256");
    			byte[] messageDigest = md.digest(seed.getBytes());
                BigInteger no = new BigInteger(1, messageDigest); 
                String hashtext = no.toString(16); 
                while (hashtext.length() < 32) { 
                    hashtext = "0" + hashtext; 
                } 
                String uId = f0lwr + hashtext.substring(0, 8);
                return uId;
    		} catch (NoSuchAlgorithmException e) {
    			return "An error occured while generating UID";
    		}         
    	}
    }
 
}
