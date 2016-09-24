/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: UserSession.java
 * Purpose	: User Session class   
 * Author	: Sergey K
 * Created	: 10/08/2016
 */


package liveu.tvbroadcast;

import java.io.Closeable;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.Continuation;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.RtpEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.internal.server.KurentoServerException;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

import redsoft.dsagent.*;


public class UserSession implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(UserSession.class);
	private final Ds ds = new Ds(getClass().getSimpleName());

	private String name;
	private float geoX = 0;
	private float geoY = 0;
	private Random random = new Random();
	
	private final WebSocketSession session;

	private MediaPipeline pipeline; // room and selected video pipeline

	private Room room;
	private WebRtcEndpoint outgoingMedia;
	private WebRtcEndpoint nextVideoMedia; // media 4 preview (next video)
	
	private RtpEndpoint outgoingCentralMedia; // outgoing video to central
	
	private final ConcurrentMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, WebRtcEndpoint> incomingLiveMedia = new ConcurrentHashMap<>();

	private boolean isLiveState = false; // is user on live state
	
	/* constructor used to create UserSession without users name, room name, and media
	 * all this will be added later if needed
	 */
	public UserSession(final WebSocketSession session) {
		ds.print("UserSession object created: %s", session.getId());
		this.session = session;
	}
	

	/*
	 * set media pipeline, create EPs
	 */
	public void setMediaPipeline(MediaPipeline p) {
		ds.print("Set MediaPipeline: name: %s, pipeline: %s", name, p.getName());
		this.pipeline = p;

		createOutgoingMedia();
		createNextVideoMedia();
	}
	
	
	public MediaPipeline getMediaPipeline() {
		return this.pipeline;
	}

	
	/*
	 * create outgoing media, bind ice candidates handler
	 */
	private void createOutgoingMedia() {
		this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.outgoingMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

			@Override
			public void onEvent(OnIceCandidateEvent event) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "iceCandidate");
				response.addProperty("name", name);
				response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
				try {
					synchronized (session) {
						session.sendMessage(new TextMessage(response.toString()));
					}
				} catch (IOException e) {
					log.debug(e.getMessage());
				}
			}
		});
	}

	
	/*
	 * create selected participant media, bind ice candidates handler
	 */
	private void createNextVideoMedia() {
		this.nextVideoMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.nextVideoMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

			@Override
			public void onEvent(OnIceCandidateEvent event) {
				JsonObject response = new JsonObject();
				response.addProperty("id", "iceCandidate4NextVideo");
				response.addProperty("name", name);
				response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
				try {
					synchronized (session) {
						session.sendMessage(new TextMessage(response.toString()));
					}
				} catch (IOException e) {
					log.debug(e.getMessage());
				}
			}
		});
	}


	public WebRtcEndpoint getOutgoingWebRtcPeer() {
		return outgoingMedia;
	}
	
	public WebRtcEndpoint getNextVideoWebRtcPeer() {
		return nextVideoMedia;
	}

	public String getName() {
		return name;
	}

	public void setName(String n) {
		this.name = n;
	}
	
	public float getGeoX() {
		return this.geoX;
	}
	
	public void setGeoX(float x) {
		if (Settings.TEST_MODE) {
			if (this.geoX == 0) {
				this.geoX = random.nextFloat() * 50;
			}
			return;
		}
		this.geoX = x;
	}

	public float getGeoY() {
		return this.geoY;
	}
	
	public void setGeoY(float y) {
		if (Settings.TEST_MODE) {
			if (this.geoY == 0) {
				this.geoY = random.nextFloat() * 20 + 30;
			}
			return;
		}
		this.geoY = y;
	}

	public WebSocketSession getSession() {
		return session;
	}

	public Room getRoom() {
		return this.room;
	}

	public void setRoom(Room r) {
		this.room = r;
	}

	public boolean getLiveState() {
		return isLiveState;
	}
	
	public void setLiveState(boolean state) {
		isLiveState = state;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer("UO:");
		if (name != null) {
			sb.append(name);
		} else {
			sb.append("NULL");
		}
		return sb.toString();
	}
	
	/* handle video request on preview video */
	public void processSdp4NextVideo(String sdpOffer) throws IOException {
		log.trace("{}: SdpOffer selected paticipant video {}", this, sdpOffer);
		ds.print("User '%s' requesting selected participant video in room '%s'", this, room.getName());

		final String ipSdpAnswer = nextVideoMedia.processOffer(sdpOffer);
		final JsonObject m = new JsonObject();
		m.addProperty("id", "sdpAnswer4NextVideo");
		m.addProperty("sdpAnswer", ipSdpAnswer);

		log.trace("{}: SdpAnswer for selected paticipant video is {}", this, ipSdpAnswer);
		ds.print("User '%s': SDP answer for sender selected paticipant video is: %s", this.name, ipSdpAnswer);
		this.sendMessage(m);
		log.debug("{}: gather candidates 4 next video", this);
		nextVideoMedia.gatherCandidates();
	}

	
	/* handle video request on live video */
	public void processSdp4Live(UserSession sender, String sdpOffer) throws IOException {
		log.info("{}: connecting LIVE with {} in room {}", this, sender, room.getName());
		log.info("{}: SdpOffer LIVE video for {} is {}", this, sender, sdpOffer);
		//ds.print("User '%s' requesting LIVE video in room '%s'", this, room.getName());
		
		WebRtcEndpoint ep = getLiveEndpointForUser(sender);
		if (ep == outgoingMedia) {			
			// do nothing with outgoing, already connected
			return;
		}
		
		String ipSdpAnswer = ep.processOffer(sdpOffer);
		sender.getOutgoingWebRtcPeer().connect(ep);
		ep.gatherCandidates();
		log.debug("{}: gather candidates 4 live video", this);
		
		final JsonObject m = new JsonObject();
		m.addProperty("id", "sdpAnswer4Live");
		m.addProperty("name", sender.getName());
		m.addProperty("sdpAnswer", ipSdpAnswer);

		log.info("{}: SdpAnswer for LIVE for {} is {}", this, sender, ipSdpAnswer);
		//ds.print("User '%s': SDP answer for sender %s LIVE is: %s", this, sender, ipSdpAnswer);
		sendMessage(m);
	}

	
	/*
	 * some participant wants video from as
	 * */
	public void receiveVideoFrom(UserSession sender, String sdpOffer) throws IOException {
		log.info("{}: connecting with {} in room {}", this, sender, room.getName());
		log.trace("{}: SdpOffer for {} is {}", this, sender, sdpOffer);
		ds.print("User '%s' connecting with '%s' in room '%s'", this.name, sender.getName(), room.getName());

		WebRtcEndpoint ep = getEndpointForUser(sender);
		final String ipSdpAnswer =	ep.processOffer(sdpOffer);
		if (ep != outgoingMedia) { // don't connect myself ??? are we need this?			
			sender.getOutgoingWebRtcPeer().connect(ep);
		}
		ep.gatherCandidates();
		log.debug("{}: gather candidates", this);
		
		final JsonObject msg = new JsonObject();
		msg.addProperty("id", "receiveVideoAnswer");
		msg.addProperty("name", sender.getName());
		msg.addProperty("sdpAnswer", ipSdpAnswer);

		log.trace("{}: SdpAnswer for {} is {}", this, sender, ipSdpAnswer);
		ds.print("User '%s': SDP answer for sender '%s' is: %s", this, sender, ipSdpAnswer);
		sendMessage(msg);
		// moved higher getEndpointForUser(sender).gatherCandidates();
	}

	
	/*
	 * get or create incoming endpoint 4 room participant 
	 */
	private WebRtcEndpoint getEndpointForUser(final UserSession sender) {
		if (sender.getName().equals(name)) {
			log.debug("{}: configuring loopback", this);
			ds.warning("User '%s' configuring loopback", this);
			return outgoingMedia;
		}

		log.debug("{}: receiving video from {}", this, sender);
		ds.print("User '%s': receiving video from: %s", this.name, sender.getName());

		WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
		if (incoming == null) {
			log.debug("{}: creating new endpoint for {}", this, sender);
			ds.print(2, "new WebRtcEndpoint");
			incoming = new WebRtcEndpoint.Builder(pipeline).build();

			incoming.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
					response.addProperty("name", sender.getName());
					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
					try {
						synchronized (session) {
							session.sendMessage(new TextMessage(response.toString()));
						}
					} catch (IOException e) {
						log.debug(e.getMessage());
					}
				}
			});

			incomingMedia.put(sender.getName(), incoming);
		}

		log.debug("{}: obtained endpoint for {}", this, sender);
//		sender.getOutgoingWebRtcPeer().connect(incoming);

		return incoming;
	}

	
	/*
	 * get or create incoming live endpoint 4 administrator and self 
	 */
	private WebRtcEndpoint getLiveEndpointForUser(final UserSession sender) {
		if (sender.getName().equals(name)) {
			log.debug("{}: configuring loopback for live", this);
			ds.warning("User '%s' configuring loopback for live", this);
			return outgoingMedia;
		}

		log.debug("{}: receiving video for live from {}", this, sender);
		ds.print("User '%s': receiving video for live from: %s", this.name, sender.getName());

		WebRtcEndpoint incoming = incomingLiveMedia.get(sender.getName());
		if (incoming == null) {
			log.debug("{}: creating new live endpoint for {}", this, sender);

			incoming = new WebRtcEndpoint.Builder(pipeline).build();

			incoming.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate4Live");
					response.addProperty("name", sender.getName());
					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
					try {
						synchronized (session) {
							session.sendMessage(new TextMessage(response.toString()));
						}
					} catch (IOException e) {
						log.debug(e.getMessage());
					}
				}
			});

			incomingLiveMedia.put(sender.getName(), incoming);
		}

		log.debug("{}: obtained live EP for {}", this, sender);
		return incoming;
	}

	
	public void cancelVideoFrom(final UserSession sender) {
		cancelVideoFrom(sender.getName());
	}

	
	public void cancelVideoFrom(final String senderName) {
		log.debug("{}: canceling video reception from {}", this, senderName);
		final WebRtcEndpoint incoming = incomingMedia.remove(senderName);

		log.debug("{}: removing endpoint for {}", this, senderName);
		
		if (incoming == null) {
			log.error("{}: incoming for {} is NULL!", this, senderName);
			ds.print("PARTICIPANT %s: incoming for %s is NULL!", this, senderName);
			return;
		}
		
		incoming.release(new Continuation<Void>() {
			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("{}: Released successfully incoming EP for {}", this, senderName);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("{}: Could not release incoming EP for {}", this, senderName);
			}
		});
	}
	

	void suspendVideoFrom(UserSession sender) {
		log.info("{}: suspending with {} in room {}", this, sender, room);
		WebRtcEndpoint ep = getEndpointForUser(sender);
		sender.getOutgoingWebRtcPeer().disconnect(ep);
	}
	
	void resumeVideoFrom(UserSession sender) {
		log.info("{}: resuming with {} in room {}", this, sender, room);
		WebRtcEndpoint ep = getEndpointForUser(sender);
		sender.getOutgoingWebRtcPeer().disconnect(ep);		
	}
	
	@Override
	public void close() throws IOException {
		log.debug("{}: releasing resources", this);
		releaseIncoming();
		releaseLiveIncoming();
		
		outgoingMedia.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("{}: released outgoing EP", this);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("{}: could not release outgoing EP", this);
			}
		});

		nextVideoMedia.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("{}: released next video EP", this);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("{}: could not release next video EP", this);
			}
		});

	}

	
	/*
	 * release incoming EPs
	 */
	public void releaseIncoming() {	
		for (final String remoteParticipantName : incomingMedia.keySet()) {

			log.trace("{}: released incoming EP for {}", this, remoteParticipantName);

			final WebRtcEndpoint ep = incomingMedia.get(remoteParticipantName);

			ep.release(new Continuation<Void>() {

				@Override
				public void onSuccess(Void result) throws Exception {
					log.trace("{}: released successfully incoming EP for {}", this, remoteParticipantName);
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					log.warn("{}: could not release incoming EP for {}", this,
							remoteParticipantName);
				}
			});
		}

		incomingMedia.clear();
	}
	
	
	/*
	 * release incoming LIVE EPs
	 */
	public void releaseLiveIncoming() {
		for (final String remoteLiveParticipantName : incomingLiveMedia.keySet()) {

			log.trace("{}: released incoming EP for {}", this, remoteLiveParticipantName);

			final WebRtcEndpoint ep = incomingLiveMedia.get(remoteLiveParticipantName);

			ep.release(new Continuation<Void>() {

				@Override
				public void onSuccess(Void result) throws Exception {
					log.trace("{}: released successfully incoming EP for {}", this, remoteLiveParticipantName);
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					log.warn("{}: could not release incoming EP for {}", this,
							remoteLiveParticipantName);
				}
			});
		}
		
		incomingLiveMedia.clear();			
	}
	
	
	/**
	 * send JSON message to user
	 * 
	 */
	public void sendMessage(JsonObject message) throws IOException {
		log.debug("{}: sending message {}", this, message);
		ds.print(1, "Send to   '%s': %s'", name, message.toString());
		synchronized (session) {
			session.sendMessage(new TextMessage(message.toString()));
		}
	}

	/**
	 * send text message to user
	 */
	public void sendMessage(String  message) throws IOException {
		log.debug("{}: sending message {}", this, message);
		ds.print(1, "Send to   '%s': '%s'", name, message);
		synchronized (session) {
			session.sendMessage(new TextMessage(message));
		}
		
	}
	
	/**
	 * send error message to user
	 * @param message error description
	 */
	public void sendError(String message) throws IOException {
		log.info("{}: sending error message: {}", this, message);
		JsonObject m = new JsonObject();
		m.addProperty("id", "error");
		m.addProperty("message", message);
		sendMessage(m);
	}
	
	
	/*
	 * add ice candidate 4 participants
	 */
	public void addCandidate(IceCandidate candidate, String name) {
		if (this.name.compareTo(name) == 0) {
			outgoingMedia.addIceCandidate(candidate);
		} else {
			WebRtcEndpoint webRtc = incomingMedia.get(name);
			if (webRtc != null) {
				webRtc.addIceCandidate(candidate);
			}
		}
	}

	
	/*
	 * add ice candidate 4 live
	 */
	public void addLiveCandidate(IceCandidate candidate, String name) {
		if (this.name.compareTo(name) == 0) {
			outgoingMedia.addIceCandidate(candidate);
		} else {
			WebRtcEndpoint webRtc = incomingLiveMedia.get(name);
			if (webRtc != null) {
				webRtc.addIceCandidate(candidate);
			}
		}
	}

	
	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}
		if (obj == null || !(obj instanceof UserSession)) {
			return false;
		}
		UserSession other = (UserSession) obj;
		boolean eq = name.equals(other.name);
		eq &= room.getName().equals(other.room.getName());
		return eq;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		int result = 1;
		result = 31 * result + name.hashCode();
		result = 31 * result + room.getName().hashCode();
		return result;
	}
}
