/* KEYSTONE MENU LIST SERVLET  -------------------------------------------------------------
   Author: Ryan Ahern
   Date: MaY 31, 2012
   
   Returns data in name-value pairs for use in menus.
   This servlet takes the following inputs:
   + Form - The form from which to fetch data
   + Qual - The qualification to use when fetching menu data
   + NameId - The field ID that will be fetched for the menu name
   + ValueId - The field ID that will be fetched for the menu value
-------------------------------------------------------------------------------------------- */

import java.io.IOException;
import java.net.*;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import com.bmc.arsys.api.*;

import java.io.PrintWriter;
import org.json.JSONObject;
import org.json.JSONArray;

public class FileDownloadServlet extends KeystoneServletBase {
	// Constants ----------------------------------------------------------------------------------
    private String encodedCSV	    = "NONE";
	
	/* Enable this variable to see error messages */
	private boolean DEBUG_ENABLED   = true;

	public FileDownloadServlet() {
		super();
	}
	
	protected void doGet( HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		doPost(request, response);
	}
	
	protected void doPost( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
		this.ClearParamMap();
		this.GetProperties();
        this.ParseParameters( request );
		
        encodedCSV = this.GetParameter("Payload");
        
        if ( !encodedCSV.equals("NONE") ) {
            this.GetFile( response, encodedCSV );
        	//PrintWriter pw = response.getWriter();
            //pw.println( BuildQualification() );
            //pw.close();
        }
        else {
            PrintWriter pw = response.getWriter();
            pw.println("Error: Servlet requires parameter \"Payload\" to be provided.");
            pw.close();
        }
	} 
	
    private void GetFile( HttpServletResponse response, String payload ) throws IOException 
    {
    	try
    	{
    		byte[] 				csvarray = payload.getBytes();
    		int                 length   = 0;
            ServletOutputStream op       = response.getOutputStream();
            ServletContext      context  = getServletConfig().getServletContext();
            
            response.reset();
            response.setBufferSize((int)payload.length());
            response.setContentType("text/comma-separated-values");
            response.setContentLength((int)payload.length());
            length = (int)payload.length();
            response.setHeader( "Content-Disposition", "attachment; filename=\"tickets.csv\"" );
            
            
            
            //
            //  Stream to the requester.
            //
            byte[] bbuf = new byte[1024];
            op.write(csvarray,0,length);
            /*
            DataInputStream in = new DataInputStream(new FileInputStream(f));

            while ((in != null) && ((length = in.read(bbuf)) != -1))
            {
                
            }

            in.close();*/
            op.flush();
           
    	}
    	catch(Exception e)
    	{
    		
    	}
    	
        
        
    }
    
}
