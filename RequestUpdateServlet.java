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

public class RequestUpdateServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
	private String ENTRY_ID 		= "NONE";
	private boolean DEBUG_ENABLED   = true; /* Enable this variable to see error messages */

	public RequestUpdateServlet() {
		super();
        this.SetServletName( "RequestSubmitServlet" );
        SetForm( "HPD:Help Desk" );
	}
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		this.ClearParamMap();
		this.GetProperties();
        this.ParseParameters( request );
        
        ENTRY_ID = GetParameter("id");
        
        if ( !ENTRY_ID.equals("NONE") ) {
            this.UpdateRequest( response );
        }
        else {
            PrintError(response, "Error: No Field IDs provided.");
        }
	} 
    
    private void UpdateRequest( HttpServletResponse response ) {

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
                PrintError( response, e.getMessage() );
                server.logout();
            }
            
            /* Build entry */
            Entry newEntry = new Entry();
            
            Iterator it = GetParameterIterator();
            while ( it.hasNext() ) {
                Map.Entry param = (Map.Entry)it.next();
                
                /* Ignore parameters that are not Remedy Fields */
                if ( !param.getKey().toString().equals("id") ) {
                    newEntry.put( Integer.valueOf(param.getKey().toString()), new Value(param.getValue().toString()) );
                }
            }
            
            try {
        		server.setEntry( GetForm(), ENTRY_ID, newEntry, new Timestamp(new Date()), 0 );
        	}
            catch ( ARException e ) {
                PrintError( response, e.getMessage() );
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
                
                PrintError( response, GetServletName() + " Error: " + e.getMessage() + " (Form: " + GetForm() + ")" );

            }
        }
    }
    
}
