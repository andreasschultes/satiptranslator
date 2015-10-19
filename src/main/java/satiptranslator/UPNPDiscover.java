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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class UPNPDiscover implements Runnable{

	static final Logger log = Logger.getLogger(UPNPDiscover.class.getName() );
	
	public static Boolean UPNPDiscoverStart()
	{
		Thread t = null;
		try {
			Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
			
			for(NetworkInterface netint : Collections.list(nets))
			{
				Enumeration<InetAddress> iAdds=netint.getInetAddresses();
				if(!netint.supportsMulticast())
					continue;
				if(netint.isLoopback())
					continue;
				for(InetAddress iAdd : Collections.list(iAdds))
				{
					if(iAdd instanceof Inet4Address)//SATIP don't support IPv6
					{
						t=new Thread(new UPNPDiscover(iAdd));
						t.start();
					}
				}
			}
			while(mAddress==null)
			{
			try {
				
				t.join(6000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return true;
			}
			}
		} catch (SocketException e) {
			
			e.printStackTrace();
		}
		return false;
	}

	private final static String DISCOVER_SATIP_SERVER=
			"M-SEARCH * HTTP/1.1\r\n"+
			"HOST: 239.255.255.250:1900\r\n"+
			"MAN: \"ssdp:discover\"\r\n"+
			"MX: 2\r\n"+
			"ST: urn:ses-com:device:SatIPServer:1\r\n"+
			"USER-AGENT: OS/version UPnP/1.1 product/version\r\n"+
			"\r\n";

	private final static Pattern ST=Pattern.compile("ST:urn:ses-com:device:SatIPServer:1");
	
	public UPNPDiscover(InetAddress netAdd)
	{
		mLocalAddress=netAdd;
	}
	
	final InetAddress mLocalAddress ;
	
	MulticastSocket mSocket;

	static InetAddress multicastAddress;

	private static InetAddress mAddress;
	public void run() {
		
		try {
			multicastAddress = InetAddress.getByName("239.255.255.250");
			final int ssdp_port=1900;
			//Take first Address of Network Adaptor to bind the socket to the network interface
			mSocket=new MulticastSocket(new InetSocketAddress(mLocalAddress,0));
			mSocket.setReuseAddress(true);
			mSocket.setSoTimeout(2000);
			mSocket.setTimeToLive(255);
			mSocket.setLoopbackMode(true);

			mSocket.joinGroup(multicastAddress);

			byte[] sendMessage=DISCOVER_SATIP_SERVER.getBytes("UTF-8");
			DatagramPacket sendPacket=new DatagramPacket(sendMessage,sendMessage.length,multicastAddress,ssdp_port);

			mSocket.send(sendPacket);

			byte[] buffer=new byte[8192];
			DatagramPacket recvPacket=new DatagramPacket(buffer,buffer.length);
		

			while(true)
			{
				try
				{			
				mSocket.receive(recvPacket);
				
			//	System.out.println(new String(recvPacket.getData()));
				
				if(AnalyisePacket(recvPacket))
					return;
				}
				catch(SocketTimeoutException e)
				{
					mSocket.send(sendPacket);
					//this.notifyAll();
					log.warning("UPNP Timeout: "+ mLocalAddress);
					return;
				}
			}
		} catch (UnknownHostException e) {
			log.warning("Wrong Host");
			e.printStackTrace();
		} catch (IOException e) {
			log.warning("Can't join multicast");

			e.printStackTrace();
		}
		
		
		
		
	}
	
	private Boolean AnalyisePacket(DatagramPacket packet) 
	{
		BufferedReader in=new BufferedReader(new StringReader(new String(packet.getData())));
		
		String line;
		try {
			while((line =in.readLine()) != null)
			{
				Matcher match=ST.matcher(line);
				if(match.find())
				{
					SetAddress(packet.getAddress());
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	synchronized void SetAddress(InetAddress address)
	{
		mAddress = address;
	}
	synchronized static InetAddress GetAddress()
	{
		return mAddress;
	}
	protected void finalize() 
	{
		try {
			mSocket.leaveGroup(multicastAddress);
		} catch (IOException e) {			
			e.printStackTrace();
		}
		mSocket.close();
	}
	
}
