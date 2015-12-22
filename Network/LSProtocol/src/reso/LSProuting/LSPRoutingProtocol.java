package reso.LSProuting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import reso.common.AbstractApplication;
import reso.common.Interface;
import reso.common.InterfaceAttrListener;
import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.ip.IPLayer;
import reso.ip.IPLoopbackAdapter;
import reso.ip.IPRouteEntry;
import reso.ip.IPRouter;
import reso.scheduler.AbstractScheduler;

public class LSPRoutingProtocol extends AbstractApplication implements IPInterfaceListener, InterfaceAttrListener {

	public static final String PROTOCOL_NAME= "LSP_ROUTING";
	public static final int IP_PROTO_LSP = Datagram.allocateProtocolNumber(PROTOCOL_NAME);
	
	private final IPLayer ip;
	public final Map<IPAddress, Adjacence> voisin = new HashMap<IPAddress,Adjacence>();
	public final Map<IPAddress, LSMessage> LSDB = new HashMap<IPAddress, LSMessage>();
	private int seqID;
	private double LSPTimeout;
	private HelloTimer helloTimer;
	private LSPTimer LSPTimer;
	
	
	/** Constructor
	 * 
	 * @param router is the router that hosts the routing protocol
	 */
	public LSPRoutingProtocol(AbstractScheduler scheduler, IPRouter router, double helloTime, double LSTimer) {
		super(router, PROTOCOL_NAME);
		this.ip = router.getIPLayer();
		this.LSPTimeout = LSTimer * 2 + 1;
		
		helloTimer = new HelloTimer(scheduler, helloTime, true, this);
		LSPTimer = new LSPTimer(scheduler, LSTimer, true, this);
		seqID = 0;
		
		helloTimer.start();
		LSPTimer.start();
	}

	private IPAddress getRouterID() {		
		IPAddress routerID= null;
		for (IPInterfaceAdapter iface: ip.getInterfaces()) {
			IPAddress addr= iface.getAddress();
			if (routerID == null)
				routerID= addr;
			else if (routerID.compareTo(addr) < 0)
				routerID= addr;
		}
		return routerID;
	}
	
	@Override
	public void start() throws Exception {
		ip.addListener(IP_PROTO_LSP, this);
		
		for (IPInterfaceAdapter iface: ip.getInterfaces())
			iface.addAttrListener(this);
		

		sendHELLO();
	}
	
	public void stop() {
		ip.removeListener(IP_PROTO_LSP, this);
		for (IPInterfaceAdapter iface: ip.getInterfaces())
			iface.removeAttrListener(this);
	}
	private void compute() {
		try {
			Dijkstra graph = new Dijkstra(getRouterID(), LSDB);
			IPAddress src = null;
			
			for( IPAddress dst : LSDB.keySet() ) {
				ArrayList<Adjacence> way = graph.GetPathTo(dst);
				if( way.size() != 0 ) {
					Adjacence lookup = way.get(way.size()-1);
						
					int min = Integer.MAX_VALUE;
					for( Map.Entry<IPAddress, Adjacence> i : voisin.entrySet()) {
						if( lookup.routeID == i.getValue().routeID && min > i.getValue().cost ) {
							min = i.getValue().cost;
							src = i.getKey();
						}
					}
					ip.removeRoute(dst);
					for (IPInterfaceAdapter iface: ip.getInterfaces()) {
						if( iface.hasAddress(src) ) {
							ip.addRoute(new IPRouteEntry(dst, iface, PROTOCOL_NAME));
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public int addMetric(int m1, int m2) {
		if (((long) m1) + ((long) m2) > Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		return m1 + m2;
	}
	public void sendLSD() throws Exception {
		LSMessage LS = new LSMessage( getRouterID(), seqID++);					
		for(Adjacence v : voisin.values()) {
			if( v.routeID == getRouterID() )
				continue;
			LS.Add(v);
		}
		LSDB.put( getRouterID(), LS );
		for( IPInterfaceAdapter dst: ip.getInterfaces() ) {
			if( dst instanceof IPLoopbackAdapter )
				continue;
			
			Datagram dm = new Datagram(dst.getAddress(), IPAddress.BROADCAST, IP_PROTO_LSP, 1, LS);
			dst.send(dm, null);
		}
	}
	public void sendHELLO() throws Exception {
		for (IPInterfaceAdapter iface: ip.getInterfaces()) {
			if (iface instanceof IPLoopbackAdapter)
				continue;
			
			HELLOMessage hello = new HELLOMessage(getRouterID());
			for( Map.Entry<IPAddress, Adjacence> i : voisin.entrySet()) {
				hello.Add( i.getValue().routeID );
			}
			Datagram dm = new Datagram(iface.getAddress(), IPAddress.BROADCAST, IP_PROTO_LSP, 1, hello);
			iface.send(dm, null);
		}
	}
	@Override
	public void receive(IPInterfaceAdapter iface, Datagram msg) throws Exception {
		
		if( msg.getPayload() instanceof HELLOMessage ) {
			System.out.println(((int) (host.getNetwork().getScheduler().getCurrentTime() * 1000)) + "ms " + host.name + " " + iface + " " + msg);
			HELLOMessage m = (HELLOMessage) msg.getPayload();
			
			if( !voisin.containsKey( iface.getAddress() ) ) {
				if( !m.contains( getRouterID() ) ) {
					// Je me figure pas dans ce message HELLO. Je m'y ajoutes.
					m.Add( m.GetOrigin() );
				}
				else {
					// Sur cette interface, je suis adjacent avec...
					voisin.put( iface.getAddress(), new Adjacence( m.GetOrigin(), iface.getMetric()) );
					sendLSD();
				}
				
				m.SetOrigin( getRouterID() );
				Datagram dm = new Datagram(iface.getAddress(), msg.src, IP_PROTO_LSP, 1, m);
				iface.send(dm, msg.src);
			}
			else {
				sendLSD();
			}
		}
		else if( msg.getPayload() instanceof LSMessage ) {
			LSMessage m = (LSMessage) msg.getPayload();
			
			// Si on le connait pas, ou que le num�ro de s�quence est plus grand:
			if( !LSDB.containsKey(m.getOrigin()) ||
				LSDB.get(m.getOrigin()).getSequenceID() < m.getSequenceID() ) {
				
				LSDB.put( m.getOrigin(), m );
				compute();
				
				for( IPInterfaceAdapter dst: ip.getInterfaces() ) {
					if( dst instanceof IPLoopbackAdapter || dst == iface )
						continue;
					
					Datagram dm = new Datagram(dst.getAddress(), IPAddress.BROADCAST, IP_PROTO_LSP, 1, m);
					dst.send(dm, null);
				}
			}
		}
	}

	@Override
	public void attrChanged(Interface iface, String attr) {
		System.out.println("attribute \"" + attr + "\" changed on interface \"" + iface + "\" : " + iface.getAttribute(attr));
		IPAddress src = ((IPInterfaceAdapter)iface).getAddress();
		
		if( attr.equals("metric") ) {
			
			for( Map.Entry<IPAddress, Adjacence> i : voisin.entrySet()) {							
				if( i.getKey() == src ) {
					i.getValue().cost = Integer.parseInt(iface.getAttribute(attr).toString());
				}
			}
			
			try {
				sendLSD();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if( attr.equals("state") ) {
			
			if( iface.isActive() == false ) {

				voisin.remove(src);
				
				try {
					sendLSD();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
}
