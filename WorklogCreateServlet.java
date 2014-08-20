/* KEYSTONE REQUEST CREATE SERVLET ---------------------------------------------------------
   Author: Ryan Ahern
   Date: June 2, 2012
   
-------------------------------------------------------------------------------------------- */

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;

import java.io.PrintWriter;

public class WorklogCreateServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
    private String FORM_NAME	    = "HPD:WorkLog";
	
	/* Enable this variable to see error messages if Attachments/IDs/etc are not found */
	private boolean DEBUG_ENABLED   = true;

	public WorklogCreateServlet() {
		super();
        this.SetServletName( "WorklogCreateServlet" ); 
	}
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		this.ClearParamMap();
		this.GetProperties();
        this.ParseParameters( request );
        
        String requestId = GetParameter("id");
        if ( !requestId.equals("NONE") ) {
            this.CreateRequest( response, requestId );
        }
        else {
            this.PrintError(response, "Error: Servlet requires parameter \"id\" to be provided."); 
        }
	} 
    
    private void CreateRequest( HttpServletResponse response, String requestId ) {

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
            newEntry.put( 1000000161, new Value(requestId) );
            
            Iterator it = GetParameterIterator();
            while ( it.hasNext() ) {
                Map.Entry param = (Map.Entry)it.next();
                
                /* Ignore parameters that are not Remedy Fields */
                if ( (!param.getKey().toString().equals("Form")) && (!param.getKey().toString().equals("id")) ) {
                    newEntry.put( Integer.valueOf(param.getKey().toString()), new Value(param.getValue().toString()) );
                }
            }
            
            try {
                server.createEntry( FORM_NAME, newEntry );
            }
            catch ( ARException e ) {
                PrintWriter pw = response.getWriter();
                pw.println( "AR Error: " + e.getMessage() );
                pw.close();
                server.logout();
            }
            
            /* Print success message */
            PrintWriter pwSuccess = response.getWriter();
            pwSuccess.println( "SUCCESS" );
            pwSuccess.close();
            server.logout();
        }
        catch ( Exception e ) {
            if (DEBUG_ENABLED) {
                server.logout();
                
                try {
                    PrintWriter pw = response.getWriter();
                    pw.println(GetServletName() + " Error: " + e.getMessage() + "(Form: " + FORM_NAME + ")" );
                    pw.close();
                }
                catch ( Exception ioe ) { }
            }
        }
    }
    
}
