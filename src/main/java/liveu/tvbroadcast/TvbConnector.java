/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: Room.java
 * Purpose	: TVB Connector class   
 * Author	: Sergey K
 * Created	: 23/08/2016
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import redsoft.dsagent.*;


public class TvbConnector implements Closeable {
	private final Ds ds = new Ds(getClass().getSimpleName());

	private UserSession		operator;
	private UserSession		participant;
	private MediaPipeline 	pipeline;
	private final String 	name;

	public String getName() {
		return name;
	}

	public TvbConnector(String roomName, MediaPipeline pipeline) {
		ds.instanceNew(roomName);
		this.name = roomName;
		this.pipeline = pipeline;
	}

    @Override
    public void finalize() {
		ds.instanceDel();
    }

    public void setPipeline(MediaPipeline p) {
		this.pipeline = p;
		ds.print("Set pipeline: %s", p.toString());
	}
	
	public MediaPipeline getPipeline() {
		ds.print("Get pipeline");
		return this.pipeline;
	}
	
	@PreDestroy
	private void shutdown() {
		this.close();
	}

	public void join(UserSession user) throws IOException {
		ds.print("Join room: room=%s, user=%s", this.name, user.getName());
		if (participant != null)
			return; // TODO - error
		participant = user;
		// TODO
		//user.setRoomName(this.name);
		//user.setMediaPipeline(this.pipeline);
		//notifyParticipants(user);
		//sendParticipants2User(user);
		
		return;
	}

	public void leave(UserSession user) throws IOException {
		ds.print("Leave room: room=%s, user=%s", this.name, user.getName());
		if (participant != user)
			return; // TODO - error
		//this.removeParticipant(user.getName());
		//user.setRoomName(null);
	}
	
	
	/**
	 * send message 2 room participants
	 * @param message
	 * @return collection of user names
	 * @throws IOException
	 */
	public Collection<String> sendMessage(JsonObject message) throws IOException {

		final List<String> participantsList = new ArrayList<>(2);

		try {
			operator.sendMessage(message);
			participant.sendMessage(message);
		} catch (final IOException e) {
			//log.debug("ROOM {}: participant {} could not be notified", name, participant.getName(), e);
			ds.error(e.toString());
		}
		participantsList.add(operator.getName());
		participantsList.add(participant.getName());

		return participantsList;		
	}
	

	/**
	 * notify room participants about new user
	 * @param newParticipant
	 * @return
	 * @throws IOException
	 */
	private Collection<String> notifyParticipants(UserSession newParticipant) throws IOException {
		final JsonObject newParticipantMsg = new JsonObject();
		newParticipantMsg.addProperty("id", "newParticipantArrived");
		newParticipantMsg.addProperty("name", newParticipant.getName());
		newParticipantMsg.addProperty("geox", newParticipant.getGeoX());
		newParticipantMsg.addProperty("geoy", newParticipant.getGeoY());

		//log.debug("ROOM {}: notifying other participants of new participant {}", name, newParticipant.getName());
		return sendMessage(newParticipantMsg);
	}

		
	private void removeParticipant(String name) throws IOException {
		//participants.remove(name);

		//log.debug("ROOM {}: notifying all users that {} is leaving the room", this.name, name);

		final List<String> unnotifiedParticipants = new ArrayList<>();
		final JsonObject participantLeftJson = new JsonObject();
		participantLeftJson.addProperty("id", "participantLeft");
		participantLeftJson.addProperty("name", name);
		try {
			participant.cancelVideoFrom(name);
			participant.sendMessage(participantLeftJson);
		} catch (final IOException e) {
			unnotifiedParticipants.add(participant.getName());
		}

		if (!unnotifiedParticipants.isEmpty()) {
			//log.debug("ROOM {}: The users {} could not be notified that {} left the room", this.name, unnotifiedParticipants, name);
		}

	}

	public void sendParticipants2User(UserSession user) throws IOException {
/*
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

		final JsonObject existingParticipantsMsg = new JsonObject();
		existingParticipantsMsg.addProperty("id", "existingParticipants");
		existingParticipantsMsg.add("data", participantsArray);
		existingParticipantsMsg.add("chat", getLastChat());
		//log.debug("PARTICIPANT {}: sending a list of {} participants", user.getName(), participantsArray.size());
		user.sendMessage(existingParticipantsMsg);
*/
	}


	@Override
	public void close() {
		try {
			participant.close();
			operator.close();
		} catch (IOException e) {
			//log.debug("ROOM {}: Could not invoke close on participant {}", this.name, user.getName(), e);
		}

		pipeline.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				//log.trace("ROOM {}: Released Pipeline", Room.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				//log.warn("PARTICIPANT {}: Could not release Pipeline", Room.this.name);
			}
		});

		//log.debug("Room {} closed", this.name);
	}

}
