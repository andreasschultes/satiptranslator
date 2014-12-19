/*
 * 
 * SATIP-Translator accepts an RTSP request from a VLC-Player and translates and
 * send the RTSP request to a SATIP-Server
    Copyright (C) 2014  Andreas Schultes

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SATIPServer {
	static final Logger log = Logger.getLogger(SATIPTranslator.class.getName() );
	ServerSocket mSocket;
	InetAddress mSatipServer;
	AtomicInteger Session;
	//Sender sender;
//	private final static Pattern status= Pattern.compile("RTSP/\\d.\\d +(\\d+) +(\\w+)");
	
	private final static Pattern options= Pattern.compile("OPTIONS rtsp:(.*) RTSP/1.0");
	private final static Pattern describe= Pattern.compile("DESCRIBE rtsp:(.*)/?(\\?.*) RTSP/1.0");
	private final static Pattern setup= Pattern.compile("SETUP rtsp:(.*) RTSP/1.0");
	private final static Pattern play= Pattern.compile("PLAY rtsp:(.*) RTSP/1.0");
	private final static Pattern teardown= Pattern.compile("TEARDOWN rtsp:(.*) RTSP/1.0");
	
	private final static Pattern cseq= Pattern.compile("CSeq: *(\\d+)");
	private final static Pattern transport= Pattern.compile("Transport: RTP/AVP/?U?D?P?;.*cast;(destination=(.*);)?client_port=(.*)");
	private final static Pattern session_matcher =Pattern.compile("Session:(.*)");
	public SATIPServer(InetAddress satip_server_address,int listen_port)
	{
	//	sender=sender_;
		mSatipServer=satip_server_address;
		Session=new AtomicInteger();
		try {
			OpenSocket(listen_port);
		} catch (IOException e) {
			log.severe("Couldn't open socket "+listen_port+"\nReason: "+e.getMessage());
			//e.printStackTrace();
		}
	}
	
	void OpenSocket(int port) throws IOException
	{
		mSocket=new ServerSocket(port);
		log.info("Listening on "+port);
		
	}
	
	private class SATIPConnection implements Runnable 
	{
		SATIPClient satipclient;
		Socket clientSocket;
		BufferedOutputStream out;
		BufferedReader in;
		String address;
		String query;
		int mCseq;
		
		

		public SATIPConnection(Socket s) throws IOException
		{
			satipclient=new SATIPClient(mSatipServer);
			clientSocket=s;
			s.setSoTimeout(20000);
			mCseq=-1;
			
			out = new BufferedOutputStream(clientSocket.getOutputStream());
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		}

		public void run() {
			String line=null;
			do
			{
				try 
				{
					line=in.readLine();
					log.fine("RecieveMessage\n");
					readLine(line);
				}			
			catch(SocketTimeoutException e)
			{
				log.finer("TIMEOUT keep SATIP CONNECTION ALIVE");
				try {
					satipclient.Option();
				} catch (SocketException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			 catch (IOException e) {
				log.finer("Thread Stop:"+mCseq+" Reason: "+e.getMessage());
				return;
			}
			}while(line!=null);
		}
	
		public void readLine(String line) throws IOException
		{
			if(line==null)
				return;
			Matcher match;
			match=options.matcher(line);
			if(match.find())
			{
				//address=match.group(1);
				//query=match.group(2);
				log.finer(line);
			//	System.out.println(match.group(1));
				while((line=in.readLine())!=null&& line.length()>3)
				{
					match=cseq.matcher(line);
					if(match.find())
					{
						mCseq=Integer.parseInt(match.group(1));
					}
					if(log.isLoggable(Level.FINER))
						System.out.println(line);
				}
				//System.out.println("------");
	
				//
				String response="RTSP/1.0 200 OK\r\n";
					   response+="CSeq:"+ mCseq+"\r\n";
					   response+="Public:OPTIONS,SETUP,PLAY,TEARDOWN,DESCRIBE\r\n";
					   response+="\r\n";//\r\n";
				out.write(response.getBytes(StandardCharsets.UTF_8));
				out.flush();
				satipclient.Option();
				log.finer("Send Message\n"+response);
				return;
			}
			
			match=describe.matcher(line);
			if(match.find())
			{
				address=match.group(1);
				query=match.group(2);
				log.finer(line);
				if(log.isLoggable(Level.FINER))
					System.out.println("Received from "+address+ " with query:"+query);

				while((line=in.readLine())!=null&& line.length()>3)
				{
					match=cseq.matcher(line);
					if(match.find())
					{
						mCseq=Integer.parseInt(match.group(1));
					}
					if(log.isLoggable(Level.FINER))
						System.out.println(line);					
				}
		//		System.out.println("------");
				//String server_address="192.168.155.232";
				String server_address=clientSocket.getLocalAddress().getHostAddress();
				String sdp="v=0\r\n";
				       sdp+="o=- 389219875 389219875 IN IP4 "+server_address+"\r\n";
				       sdp+="s=RTSP Session\r\n";
				    //   sdp+="i=An Example of RTSP Session Usage\r\n";
				       sdp+="t=0 0\r\n";
				       
				   //    sdp+="a=control:rtsp://"+clientSocket.getLocalAddress().getHostAddress()+"/\r\n";
				       sdp+="m=video 0 RTP/AVP 33\r\n";
				       sdp+="c=IN IP4 0.0.0.0\r\n";
				       sdp+="a=control:stream=39\r\n";
				       //sdp+="a=control:stream=34\r\n";
				      // sdp+="a=control:rtsp://192.168.155.232/?src=1\r\n";
				       sdp+="a=fmtp:33 ver=1.0;src=1;tuner=1,93,1,15,12188.00,h,dvbs,qpsk,off,0.35,27500,34;pids=0,18,47,71,136,167\r\n";
				       sdp+="a=sendonly\r\n";
				       
				       
				String response="RTSP/1.0 200 OK\r\n";
					   
					   response+="Content-Type:application/sdp\r\n";
					   response+="Content-Base:rtsp://"+server_address+"/\r\n";
					   response+="CSeq:"+ mCseq+"\r\n";
					   response+="Content-Length: "+(sdp.length())+"\r\n";
					   response+="\r\n";
					   response+=sdp;
					
				out.write(response.getBytes(StandardCharsets.UTF_8));
				out.flush();	   
			
				log.finer("Send Message\n"+response);
				return;
			}
			
			match=setup.matcher(line);
			if(match.find())
			{
				address=match.group(1);
				log.finer(line);
				String client_ports="";
				//String destination="239.0.1.1";
				String destination=null;
				while((line=in.readLine())!=null&& line.length()>3)
				{
					match=cseq.matcher(line);
					if(match.find())
					{
						mCseq=Integer.parseInt(match.group(1));
					}
					match=transport.matcher(line);
					if(match.find())
					{
					//	destination=match.group(2);
						client_ports=match.group(3);
						if(log.isLoggable(Level.FINEST))
							System.out.println(destination+":"+client_ports);
					}
					if(log.isLoggable(Level.FINER))
						System.out.println(line);
					
				}
				//System.out.println("SATIPCLIENT------SETUP");
	
				satipclient.Setup( query,destination,client_ports);

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//System.out.println("SATIPSERVER------SETUP");
				//
				String response="RTSP/1.0 200 OK\r\n";
					   response+="CSeq:"+ mCseq+"\r\n";
					   response+="Session:"+Session.get()+";timeout=15\r\n";
				if(destination!=null)
					   response+="Transport:RTP/AVP;multicast;destination="+destination +";source="+mSatipServer.getHostAddress()+";port="+client_ports/*+";server_port="+satipclient.GetServerPort()*/ +";ttl=5\r\n";
				else
					   response+="Transport:RTP/AVP;unicast;destination="+satipclient.GetDestination() +";source="+mSatipServer.getHostAddress()+";client_port="+satipclient.GetClientPort()/*+";server_port="+satipclient.GetServerPort()*/ +"\r\n";
					   response+="\r\n";//\r\n";
				out.write(response.getBytes(StandardCharsets.UTF_8));
				out.flush();
				Session.incrementAndGet();
				log.finer("Send Message\n"+response+"-------");
				return;
			}
			match=play.matcher(line);
			if(match.find())
			{
				address=match.group(1);
				log.finer(line);
				String lSession="0";
				while((line=in.readLine())!=null&& line.length()>3)
				{
					match=cseq.matcher(line);
					if(match.find())
					{
						mCseq=Integer.parseInt(match.group(1));
					}
					match=session_matcher.matcher(line);
					if(match.find())
					{
						lSession=match.group(1);						
					}
					log.finer(line);
				}
				log.finer("SATIPCLIENT------PLAY");
				satipclient.Play();
				//satipclient.Describe("192.168.155.232");
				log.finer("SATIPSERVER------PLAY");
				//
				String response="RTSP/1.0 200 OK\r\n";
					   response+="CSeq:"+ mCseq+"\r\n";
					   response+="RTP-INFO:url=rtsp://10.0.0.10\r\n";
					   //response+="RTP-Info:"+satipclient.GetRTPInfo() +"\r\n";
					   response+="Session:"+lSession+"\r\n";
					   response+="Range:npt=0.000-\r\n";
					   response+="\r\n";//\r\n";
				out.write(response.getBytes(StandardCharsets.UTF_8));
				out.flush();
				Session.incrementAndGet();
				log.finer("Send Message\n"+response+"-------");
				return;
			}
			match=teardown.matcher(line);
			if(match.find())
			{
				address=match.group(1);
				log.finer(line);
				//String lSession="0";
				while((line=in.readLine())!=null&& line.length()>3)
				{
					match=cseq.matcher(line);
					if(match.find())
					{
						mCseq=Integer.parseInt(match.group(1));
					}
					match=session_matcher.matcher(line);
					if(match.find())
					{
					//	lSession=match.group(1);						
					}
					log.finer(line);
				}
				log.finer("------");
				satipclient.Teardown();
				//
				String response="RTSP/1.0 200 OK\r\n";
					   response+="CSeq:"+ mCseq+"\r\n";
					//   response+="Session:"+lSession+"\r\n";
					//   response+="Range: npt=20-25\r\n";
					   response+="\r\n";//\r\n";
				out.write(response.getBytes(StandardCharsets.UTF_8));
				out.flush();
				Session.incrementAndGet();
				log.finer("Send Message TEARDOWN\n"+response+"-------");
				return;
			}
			log.finer(line);
			while((line=in.readLine())!=null&& line.length()>3)
			{
				log.finer(line);
				
			}
			log.finer("------");
		}

		protected void finalize()
		{
			try
			{
				if(clientSocket!=null)
				clientSocket.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
		


	}
	
	void Listen() throws IOException
	{
		if(mSocket==null)
			return;
		Socket clientSocket; 
		while((clientSocket= mSocket.accept() )!= null)
		{
			System.out.print("new Connection\n");
			new Thread(new SATIPServer.SATIPConnection(clientSocket)).start();

		}
	}
	protected void finalize()
	{
		try
		{
			if(mSocket!=null)
				mSocket.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	
}



