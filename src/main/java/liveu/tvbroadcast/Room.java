/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: Room.java
 * Purpose	: Room class   
 * Author	: Sergey K
 * Created	: 10/08/2016
 */

package liveu.tvbroadcast;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PreDestroy;

import org.kurento.client.Continuation;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.RtpEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import redsoft.dsagent.*;


public class Room implements Closeable {
	private final Logger log = LoggerFactory.getLogger(Room.class);
	private final Ds ds = new Ds(getClass().getSimpleName());

	private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
	private final LimitedQueue<JsonObject> chatMessages = new LimitedQueue<JsonObject>(20);
	private MediaPipeline pipeline;
	private final String name;
	private final String descr;
	private final String colorName;
	private UserSession selectedParticipant = null;
	private UserSession liveParticipant = null;
	private Room liveRoom = null; // target live room (tvbconnector?)
	RtpEndpoint outgoingCentralMedia = null;

	public String getName() {
		return name;
	}

	public String getDescr() {
		return descr;
	}

	public String getColorName() {
		return colorName;
	}

	public Room(String roomName, String descr, String colorName, MediaPipeline pipeline) {
		ds.instanceNew(roomName);
		this.name = roomName;
		this.descr = descr;
		this.colorName = colorName;
		this.pipeline = pipeline;
		log.info("ROOM {} has been created", roomName);
	}
	
    @Override
    public void finalize() {
		ds.instanceDel();
    }

	public MediaPipeline getPipeline() {
		ds.print("Get pipeline");
		return this.pipeline;
	}
	
	/**
	 * add chat message
	 */
	public void addChatMessage(JsonObject msg) {
		chatMessages.add(msg);
	}

	/**
	 * get last chat
	 */
	private synchronized JsonArray getLastChat() {
		JsonArray arr = new JsonArray();
		for (int i = 0; i < chatMessages.size(); i++) {
			arr.add(chatMessages.get(i));			
		}
		return arr;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer("ROOM:");
		if (name != null) {
			sb.append(name);
		} else {
			sb.append("NULL");
		}
		return sb.toString();
	}
		
	@PreDestroy
	private void shutdown() {
		this.close();
	}

	public void join(UserSession user) throws IOException {
		log.info("{}: adding participant {}", this, user);
		ds.print("Join room: room=%s, user=%s", this.name, user.getName());
		user.setRoom(this);
		user.setMediaPipeline(this.pipeline);
		if (selectedParticipant != null) {
			selectedParticipant.getOutgoingWebRtcPeer().connect(user.getNextVideoWebRtcPeer(), MediaType.VIDEO);
		}
		notifyParticipants(user);
		participants.put(user.getName(), user);
		sendParticipants2User(user);
		
		return;
	}

	public void leave(UserSession user) throws IOException {
		log.debug("{}: Leaving room {}", this, user);
		ds.print("Leave room: room=%s, user=%s", this.name, user.getName());
		removeParticipant(user);
		user.setRoom(null);
		user.close();
	}
	
	/**
	 * starts LIVE with current room selected participant and target room
	 * 
	 * @param target - room as live target
	 * 	
	 */
	public void startLive(Room target) throws IOException {
		log.info("{}: starting LIVE in {} with {}", this, target, selectedParticipant);
		if (selectedParticipant == null) {
			log.error("{}: could not start live without selected participant!", this);
			return;
		}
		
		liveParticipant = selectedParticipant;
		liveRoom = target;
		
		// notify room target users (operators or admins...)
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "newParticipantArrived");
		msg.addProperty("name", liveParticipant.getName());
		msg.addProperty("geox", liveParticipant.getGeoX());
		msg.addProperty("geoy", liveParticipant.getGeoY());

		log.debug("{}: notifying {} participants of live participant {}", this, selectedParticipant);
		target.sendMessage(msg);

		
		// send target room users (operators or admins...) to selected user going on live
		final JsonArray participantsArray = new JsonArray();
		for (final UserSession participant : target.getParticipants()) {
			JsonObject r = new JsonObject();
			r.addProperty("name", participant.getName());
			r.addProperty("geox", participant.getGeoX());
			r.addProperty("geoy", participant.getGeoY());
			participantsArray.add(r);
		}

		msg = new JsonObject();
		msg.addProperty("id", "makeLive");
		msg.add("data", participantsArray);
		log.debug("{} sending a list of {} live participants", liveParticipant, participantsArray.size());
		liveParticipant.sendMessage(msg);
		return;		
	}
	
	
	/**
	 * stop LIVE and return to current room
	 */
	public void stopLive() throws IOException {
		log.info("{}: stoping LIVE in {} with {}", this, liveRoom, liveParticipant);
		liveRoom.removeParticipant(liveParticipant);
		liveRoom = null;
	}
	
	
	/**
	 * get LIVE target room (or tvbconnector)
	 */
	public Room getLiveRoom() {
		return liveRoom;
	}
	
	
	/**
	 * get LIVE participant
	 */
	public UserSession getLiveParticipant() {
		return liveParticipant;
	}
	
	
	/**
	 * connect liveParticipant 2 central
	 */
	public void connectCentral() throws IOException {
		log.info("{}: connecting {} to CENTRAL", this, liveParticipant);
		if (outgoingCentralMedia != null) {
			log.error("{}: already connected to CENTRAL", this);
			return;
		}
		outgoingCentralMedia = new RtpEndpoint.Builder(pipeline).build(); 
		outgoingCentralMedia.setOutputBitrate (400000);
	    String  offer = "v=0\r\n"
	    		+ "o=- 12345 12345 IN IP4 80.96.122.73\r\n"
	    		+ "s=-\r\n"
	    		+ "c=IN IP4 80.96.122.73\r\n"
	    		+ "t=0 0\r\n"
	    		+ "m=video 52126 RTP/AVP 96 97 98\r\n"
	    		+ "a=rtpmap:96 H264/90000\r\n"
	    		+ "a=recvonly\r\n"
	    		+ "b=AS:2000\r\n"
	    		+ "m=audio 52128  RTP/AVP  0\r\n"
	    		+ "c=IN IP4 80.96.122.73\r\n"
	    		+ "a=recvonly\r\n"
	            + "a=rtpmap:0 PCMU/8000";
	
		String a = outgoingCentralMedia.processOffer(offer);
		log.info("{}: CENTRAL MEDIA ANSWER: '{}'", this, a);

	    liveParticipant.getOutgoingWebRtcPeer().connect(outgoingCentralMedia);
	    return;
	}
	
	
	/**
	 * disconnect liveParticipant from central
	 */
	public void disconnectCentral() throws IOException {
		log.info("{}: disconnecting {} from CENTRAL", this, liveParticipant);
		if (liveParticipant == null || outgoingCentralMedia == null) {
			log.error("{}: null live participant or media", this);
			return;
		}
	    liveParticipant.getOutgoingWebRtcPeer().disconnect(outgoingCentralMedia);
	    outgoingCentralMedia.release();
	    outgoingCentralMedia = null;
	}
	
	/**
	 * send message 2 room participants
	 * @param message
	 * @return collection of user names
	 * @throws IOException
	 */
	public Collection<String> sendMessage(JsonObject message) throws IOException {
		log.debug("{}: sending message {}", this, message);

		final List<String> participantsList = new ArrayList<>(participants.values().size());

		for (final UserSession participant : participants.values()) {
			try {
				participant.sendMessage(message);
			} catch (final IOException e) {
				log.debug("{}: {} could not be notified", this, participant, e);
			}
			participantsList.add(participant.getName());
		}

		return participantsList;		
	}
	

	/**
	 * notify room participants about new user
	 * @param newParticipant
	 * @return
	 * @throws IOException
	 */
	private Collection<String> notifyParticipants(UserSession newParticipant) throws IOException {
		final JsonObject msg = new JsonObject();
		msg.addProperty("id", "newParticipantArrived");
		msg.addProperty("name", newParticipant.getName());
		msg.addProperty("geox", newParticipant.getGeoX());
		msg.addProperty("geoy", newParticipant.getGeoY());

		log.debug("{}: notifying other participants of new participant {}", this, newParticipant);
		return sendMessage(msg);
	}

		
	private void removeParticipant(UserSession user) throws IOException {
		String name = user.getName();

		participants.remove(name);

		log.debug("{}: notifying all users that {} is leaving the room", this, user);

		final List<String> unnotifiedParticipants = new ArrayList<>();
		final JsonObject msg = new JsonObject();
		msg.addProperty("id", "participantLeft");
		msg.addProperty("name", name);
		for (final UserSession participant : participants.values()) {
			try {
				participant.cancelVideoFrom(name);
				participant.sendMessage(msg);
			} catch (final IOException e) {
				unnotifiedParticipants.add(participant.getName());
			}
		}

		if (!unnotifiedParticipants.isEmpty()) {
			log.debug("{}: The users {} could not be notified that {} left the room", this,
					unnotifiedParticipants, name);

			ds.print("ROOM %s: The users %s could not be notified that %s left the room", this.name,
					unnotifiedParticipants, name);}

	}

	public void sendParticipants2User(UserSession user) throws IOException {

		final JsonArray participantsArray = new JsonArray();
		for (final UserSession participant : this.getParticipants()) {
			if (!participant.equals(user)) {
				JsonObject r = new JsonObject();
				r.addProperty("name", participant.getName());
				r.addProperty("geox", participant.getGeoX());
				r.addProperty("geoy", participant.getGeoY());
				participantsArray.add(r);
			}
		}

		final JsonObject msg = new JsonObject();
		msg.addProperty("id", "existingParticipants");
		msg.add("data", participantsArray);
		msg.add("chat", getLastChat());
		if (selectedParticipant != null) {
			msg.addProperty("selected", selectedParticipant.getName());
		}
		log.debug("{} sending a list of {} participants", user, participantsArray.size());
		user.sendMessage(msg);
	}

	public Collection<UserSession> getParticipants() {
		return participants.values();
	}

	public UserSession getParticipant(String name) {
		return participants.get(name);
	}
	
	
	/**
	 * count room participants
	 * 
	 */
	public int getUsersCount() {
		return participants.size();
	}
	
	public boolean setSelectedParicipant(UserSession user) {
		if (!participants.containsKey(user.getName())) {
			log.error("no such participant {}!", user);
			return false;
		}
		
		if (selectedParticipant != null) {
			// disconnect from existing selected participant video
			for (UserSession u : participants.values()) {
				selectedParticipant.getOutgoingWebRtcPeer().disconnect(u.getNextVideoWebRtcPeer());
			}			
		}
		
		selectedParticipant = user;

		// connect from newly created selected participant
		for (UserSession u : participants.values()) {
			selectedParticipant.getOutgoingWebRtcPeer().connect(u.getNextVideoWebRtcPeer(), MediaType.VIDEO);
		}
		return true;
	}
	
	public UserSession getSelectedParticipant() {
		return selectedParticipant;
	}

	@Override
	public void close() {
		for (final UserSession user : participants.values()) {
			try {
				user.close();
			} catch (IOException e) {
				log.debug("{}: Could not invoke close on participant {}", this, user, e);
			}
		}

		participants.clear();

		pipeline.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("{}: released pipeline", this);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("{}: could not release Pipeline", this);
			}
		});

		log.debug("{}: closed", this);
	}

}
