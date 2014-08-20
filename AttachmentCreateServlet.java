/* KEYSTONE REQUEST CREATE SERVLET ---------------------------------------------------------
   Author: Ryan Ahern
   Date: June 2, 2012
   
-------------------------------------------------------------------------------------------- */

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;

/* Apache Commons File Upload Class */
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;

import java.io.PrintWriter;

public class AttachmentCreateServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
    private String FORM_NAME	    = "HPD:IncidentInterface_Create";
	
	/* Enable this variable to see error messages if Attachments/IDs/etc are not found */
	private boolean DEBUG_ENABLED   = true;

	public AttachmentCreateServlet() {
		super();
        this.SetServletName( "AttachmentCreateServlet" );
	}
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		this.ClearParamMap();
		this.GetProperties();

        this.CreateRequest( request, response );
        if ( !FORM_NAME.equals("NONE") ) {
            //this.CreateRequest( request, response );
        }
        else {
            PrintError( response, "Error: Servlet requires parameter \"Form\" to be provided.");
        }
	} 
	
	private void CreateAttachmentRequest( HttpServletRequest request, HttpServletResponse response ) {

		String responseText = "";
		try { 
			DiskFileItemFactory factory = new DiskFileItemFactory(); 
			ServletFileUpload  upload = new ServletFileUpload( factory );
			//FileItemIterator iter = upload.getItemIterator(request);
			List /* FileItem */ items = upload.parseRequest(request);
			Iterator iter = items.iterator(); 
			
			/* Get all non-attachment data */
			byte[]test = null;
			String filetest = "";
			while ( iter.hasNext() ) {
				FileItem item = (FileItem)iter.next();

				/* Normal GET/POST Parameter */
				if ( item.isFormField() ) {
					String name =  item.getFieldName();
					String value = item.getString();
					responseText += name + ": " + value + "\n";
				} 
				else { /* File data */
					String name =  item.getFieldName();
					String filename = item.getName();
					
					responseText += name + ": " + filename + "(" + item.getSize() + ")\n";
					
					AttachmentValue attachment = new AttachmentValue( filename, item.get() );
			        Value attachVal = new Value(attachment);

				}
				
			}
			
			PrintWriter pw = response.getWriter();
			pw.println( responseText );
			pw.close();
		}
		catch ( Exception e ) {
			this.PrintError( response, e.getMessage() );
		}
	}
    
    private void CreateRequest( HttpServletRequest request, HttpServletResponse response ) {
    	String formName = FORM_NAME;
    	String referralURL = "";
        /* Log into AR Server */
        com.bmc.arsys.api.ARServerUser server = new com.bmc.arsys.api.ARServerUser();
        server.setServer( this.GetConfig("server") );
        server.setUser( this.GetConfig("username") );
        server.setPassword( this.GetConfig("password") );

        if ( !this.GetConfig("port").equals("") ) {
            int port = Integer.parseInt( this.GetConfig("port") );
            if ( port > 0 ) {
                server.setPort( port );
            }
        }
        
        try {
            
            /* Check the config map to make sure there was not an error */
            if ( !this.GetConfig("ERROR").equals("") ) {
                PrintWriter pwConfig = response.getWriter();
                pwConfig.println("Configuration Error: " + this.GetConfig("ERROR") );
                pwConfig.close();
            }
            
            try {
                server.verifyUser();
            }
            catch (ARException e) {
                PrintWriter pw = response.getWriter();
                pw.println( "{\"Error\":\"[VerifyUser] " + e.getMessage() + "\"}");
                pw.close();
                server.logout();
            }
            
            /* Build entry */
            Entry newEntry = new Entry();
            
            try { 
    			DiskFileItemFactory factory = new DiskFileItemFactory(); 
    			ServletFileUpload  upload = new ServletFileUpload( factory );
    			//FileItemIterator iter = upload.getItemIterator(request);
    			List /* FileItem */ items = upload.parseRequest(request);
    			Iterator iter = items.iterator(); 
    			
    			/* Get all non-attachment data */
    			byte[]test = null;
    			String filetest = "";
    			while ( iter.hasNext() ) {
    				FileItem item = (FileItem)iter.next();

    				/* Normal GET/POST Parameter */
    				if ( item.isFormField() ) {
    					String name =  item.getFieldName();
    					String value = item.getString();

    					/* Do not include params that are not meant to be inserted into Remedy */
    					if ( name.equals("Refer") ) {
    						referralURL = item.getString();
    					}
    					else if ( name.equals("Form") ) {
    						formName = item.getString();
    					}
    					else { /* Otherwise it is a remedy field */
    						newEntry.put( Integer.valueOf(item.getFieldName()), new Value(item.getString()) );
    					}
    				} 
    				else { /* File data */
    					if ( item.getSize() > 0 ) {
	    					String filename = item.getName();
	    					AttachmentValue attachment = new AttachmentValue( filename, item.get() );
	    			        Value attachVal = new Value(attachment);
	    			        newEntry.put( Integer.valueOf(item.getFieldName()), attachVal );
    					}
    				}
    				
    			}
            }
    		catch ( Exception e ) {
    			this.PrintHTMLReferral( response, referralURL, e.getMessage() );
    		}
            
            try {
                server.createEntry( formName, newEntry );
            }
            catch ( ARException e ) {
            	server.logout();
            	this.PrintHTMLReferral( response, referralURL, e.getMessage() );
            }
            
            /* Refer user to new URL (Print no message since the user does not know the form is submitting) */
            server.logout();
            this.PrintHTMLReferral( response, referralURL, "" );
            
        }
        catch ( Exception e ) {
            if (DEBUG_ENABLED) {
                server.logout();
                
                try {
                	this.PrintHTMLReferral( response, referralURL, e.getMessage() );
                }
                catch ( Exception ioe ) { }
            }
        }
    }
    
    private void PrintHTMLReferral( HttpServletResponse response, String url, String content ) {
    	String html = "<html><head><meta http-equiv=\"refresh\" content=\"0;URL='" + url + "/'\" /></head>";    
    	html += "<body>" + content + "</body></html>";
    	
    	response.setContentType( "text/html" );
    	try {
	    	PrintWriter out = response.getWriter();
	        out.println( html );
	    	out.close();
    	}
    	catch (Exception e ) {
    		e.printStackTrace();
    	}
    }
}
