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
import java.io.IOException;
import java.util.logging.Level;
//import java.util.logging.LogManager;
import java.util.logging.Logger;
//import java.util.logging.ConsoleHandler;



public class SATIPTranslator {

	final static String HELP_MESSAGE="SATIP-Translator accept an RTSP request from VLC-Player and translate and send the RTSP request to a SATIP-Server"
							+"Possible commands: --help --port";
	final static Logger log=Logger.getLogger(SATIPTranslator.class.getName() );;

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int port=8554;
		String arg;
		for(int i=0;i<args.length;i++)
		{
			arg=args[i];
			if(arg.equals("--help"))
			{
				System.out.println(HELP_MESSAGE);
				System.exit(0);
			}
			try
			{
			if(arg.equals("--port"))
				if((i+1)<args.length)
				port=Integer.parseInt(args[i+1]);
				else
				{
					log.severe("Missing parameter");
					System.exit(-1);
				}
			}
			catch(NumberFormatException e)
			{
				log.severe("Missing or invalid port parameter");
				System.exit(-1);
			}
		}


		UPNPDiscover.UPNPDiscoverStart();
		log.info("Found SATIP at "+UPNPDiscover.GetAddress().getHostAddress());
		try
		{
			SATIPServer server=new SATIPServer(UPNPDiscover.GetAddress(),port);

			server.Listen();
		}
		catch(IOException e)
		{
			log.severe("BIGERROR:"+e.getMessage());
			System.exit(-2);
		}
		finally
		{
			log.info("ByeBye");
		}
		System.exit(0);
	}

}
