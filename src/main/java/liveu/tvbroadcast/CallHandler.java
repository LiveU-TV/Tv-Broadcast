/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: CallHandler.java
 * Purpose	: Application's main Web Sockets handler    
 * Author	: Sergey K
 * Created	: 10/08/2016
 */


package liveu.tvbroadcast;

import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.kurento.client.IceCandidate;
import org.kurento.client.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import redsoft.dsagent.*;


public class CallHandler extends TextWebSocketHandler 
{
	private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
	private static final Gson gson = new GsonBuilder().create();

	private final Ds ds = new Ds(getClass().getSimpleName());

	@Autowired
	private RoomManager roomManager;

	@Autowired
	private UserRegistry registry;

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		try {
			_handleTextMessage(session, message);
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
	

	public void _handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		ds.funcs(2, "CallHandler::handleTextMessage");
		
		final JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);

		UserSession user = registry.getBySession(session);

		if (user != null) {
			log.info("{}: incoming message: {}", user, jsonMessage);
		} else {
			log.info("Incoming message from new user: {}", jsonMessage);
			user = new UserSession(session);
			registry.register(user);
		}

		ds.print(1, "Recv from '%s': '%s'", user.getName(), jsonMessage.toString());

		switch (jsonMessage.get("id").getAsString()) {
		case "login":
			loginUser(jsonMessage, user);
			break;
		case "joinRoom":
			joinRoom(jsonMessage, user);
			break;
		case "receiveVideoFrom":
			final String senderName = jsonMessage.get("sender").getAsString();
			final UserSession sender = registry.getByName(senderName);
			final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
			user.receiveVideoFrom(sender, sdpOffer);
			break;
		case "sdpOffer4NextVideo":
			final String sdpOfferPS = jsonMessage.get("sdpOffer").getAsString();
			user.processSdp4NextVideo(sdpOfferPS);
			break;
		case "sdpOffer4Live":
			final String senderNameLive = jsonMessage.get("sender").getAsString();
			final UserSession senderLive = registry.getByName(senderNameLive);
			final String sdpOfferLive = jsonMessage.get("sdpOffer").getAsString();
			user.processSdp4Live(senderLive, sdpOfferLive);
			break;
		case "connectCentral":
			connectCentral(jsonMessage, user);
			break;
		case "disconnectCentral":
			disconnectCentral(jsonMessage, user);
			break;
		case "selectParticipant":
			selectUser(jsonMessage, user);
			break;
		case "startLive":
			startLive(jsonMessage, user);
			break;
		case "stopLive":
			stopLive(jsonMessage, user);
			break;
		case "leaveRoom":
			leaveRoom(jsonMessage, user);
			break;
		case "onIceCandidate":
			JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

			if (user != null) {
				IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(),
						candidate.get("sdpMid").getAsString(), candidate.get("sdpMLineIndex").getAsInt());
				user.addCandidate(cand, jsonMessage.get("name").getAsString());
			}
			break;
		case "onIceCandidate4NextVideo":
			JsonObject candidate4NextVideo = jsonMessage.get("candidate").getAsJsonObject();

			if (user != null) {
				IceCandidate cand = new IceCandidate(candidate4NextVideo.get("candidate").getAsString(),
						candidate4NextVideo.get("sdpMid").getAsString(), candidate4NextVideo.get("sdpMLineIndex").getAsInt());
				user.getNextVideoWebRtcPeer().addIceCandidate(cand);
			}
			break;
		case "onIceCandidate4Live":
				JsonObject candidate4Live = jsonMessage.get("candidate").getAsJsonObject();

				if (user != null) {
					IceCandidate cand = new IceCandidate(candidate4Live.get("candidate").getAsString(),
							candidate4Live.get("sdpMid").getAsString(), candidate4Live.get("sdpMLineIndex").getAsInt());
					user.addLiveCandidate(cand, jsonMessage.get("name").getAsString());
				}
				break;
		case "listRooms":
			listRooms(jsonMessage, user);
			break;
		case "addRoom":
			addRoom(jsonMessage, user);
			break;
		case "removeRoom":
			removeRoom(jsonMessage, user);
			break;
		case "chat":
			chatRoom(jsonMessage, user);
			break;
		case "geo":
			setUserLocation(jsonMessage, user);
			break;
		default:
			break;
		}
		log.info("Message handled: {}", jsonMessage);
		ds.funce(2, "CallHandler::handleTextMessage");
	}

	
	/**
	 * try to login user
	 * @param params
	 * @param user
	 * @throws IOException
	 */
	private void loginUser(JsonObject params, UserSession user) throws IOException {
		String login = params.get("login").getAsString();
		log.info("{}: trying to login as {}", user, login);
		ds.print(0, "Trying to login as '%s'", login);

		UserSession u = registry.getByName(login);
		
		if (u != null) {
			log.info("{}: already logged!", u);
			ds.warning("User '%s' already logged", login);
			JsonObject msg = new JsonObject();
			msg.addProperty("id", "login");
			msg.addProperty("result", "FAIL");
			msg.addProperty("message", "user already logged");
			user.sendMessage(msg);
			return;
		}
		
		if (user.getName() != null) {
			/* already logged */
			JsonObject msg = new JsonObject();
			msg.addProperty("id", "login");
			msg.addProperty("login", user.getName());
			msg.addProperty("result", "FAIL");
			msg.addProperty("message", "already logged as " + user.getName());
			user.sendMessage(msg);			
			return;
		}

		/* success */
		user.setName(login.toString());
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "login");
		msg.addProperty("login", user.getName());
		msg.addProperty("result", "OK");
		msg.addProperty("message", "OK");
		user.sendMessage(msg);
		log.info("{}: LOGGED IN!");
		return;
	}
	
	
	/**
	 * join user  room
	 */
	private void joinRoom(JsonObject params, UserSession user) throws IOException {
		final String roomName = params.get("room").getAsString();
		final String name = params.get("name").getAsString();
		log.info("{}: trying to join room {}", user, roomName);

		Room room = roomManager.getRoom(roomName);
		if (room == null) {
			// room no exits, this is error
			user.sendError("Room " + roomName + " not exists!");
			return;
		}
		/* !!! override user name by name from join room message */
		user.setName(name);
		
		/* set geo coord */
		//user.setGeoX(params.get("geox").getAsFloat());
		//user.setGeoY(params.get("geoy").getAsFloat());
		room.join(user);
						
		/* send update room message 2 all */
		sendUpdateRoom(room);
	}

	/**
	 * select user from next video
	 */
	private void selectUser(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for next video", user);
			return;
		}
		
		final String name = params.get("name").getAsString();
		if (name == null) {
			log.error("{}: null name 4 next video", user);
			return;
		}
		
		UserSession selectedUser = r.getParticipant(name);
		if (selectedUser == null) {
			log.error("{}: no such user for next video from {}", user, name);
			return;			
		}
		
		if (!r.setSelectedParicipant(selectedUser)) {
			log.error("{}: could not select {} 4 next video", user, selectedUser);
			return;
		}

		JsonObject msg = new JsonObject();
		msg.addProperty("id", "selectedParticipant");
		msg.addProperty("name", r.getSelectedParticipant().getName());
		r.sendMessage(msg);
		
		return;
	}
	
	/**
	 * start LIVE at room
	 */
	private void startLive(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for live video", user);
			return;
		}
		
		Room target = roomManager.getRoom("Administrator");
		if (target == null) {
			log.error("{}: null target room for live video", user);
			return;
		}
		
		r.startLive(target);
		return;
	}
	
	
	/*
	 * stop LIVE at room
	 */
	private void stopLive(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for live video", user);
			return;
		}

		r.stopLive();
		return;
	}
	
	
	/*
	 * connect 2 central
	 */
	private void connectCentral(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for central connnect", user);
			return;
		}
		
		if (r.getLiveParticipant() == null) {
			log.error("{}: null live participant in {} for central connect", user, r);
			return;
		}
		
		r.connectCentral();
		return;
	}

	
	/*
	 * disconnect from central
	 */
	private void disconnectCentral(JsonObject params, UserSession user) throws IOException {
		Room r = user.getRoom();
		
		if (r == null) {
			log.error("{}: null room for central connnect", user);
			return;
		}
		
		r.disconnectCentral();
		return;
	}

	
	/**
	 * send updateRoom message 2 all
	 */
	private void sendUpdateRoom(Room room) {
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "updateRoom");
		msg.addProperty("name", room.getName());
		msg.addProperty("descr", room.getDescr());
		msg.addProperty("color", room.getColorName());
		msg.addProperty("nusers", room.getUsersCount());
			
		log.info("ALL: sending room update message: {}", msg.toString());
		ds.print("Sending room update message");

		sendMessage2ALL(msg);				
	}
	
	
	/**
	 * set user geo location, notify room participants 
	 */
	private void setUserLocation(JsonObject params, UserSession user) throws IOException {
		/* set geo coord */
		user.setGeoX(params.get("geox").getAsFloat());
		user.setGeoY(params.get("geoy").getAsFloat());
		
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "geo");
		msg.addProperty("name", user.getName());
		msg.addProperty("geox", user.getGeoX());
		msg.addProperty("geoy", user.getGeoY());
		Room room = user.getRoom();

		if (room == null) return;

		room.sendMessage(msg);
		return;
	}
	
	
	/*
	 * user leave room
	 */
	private void leaveRoom(JsonObject params, UserSession user) throws IOException {
		Room room = user.getRoom();
		if (room != null) {
			room.leave(user);
			/* send update room message 2 all */
			sendUpdateRoom(room);
		}
	}
	
	/**
	 * room chat message
	 */
	private void chatRoom(JsonObject params, UserSession user) throws IOException {
		String uname = user.getName();
		String rname = null;
		Room room = user.getRoom();
		if (room != null) {
			rname = room.getName();
		}

		String message = params.get("message").getAsString();
		
		if (uname != null && room != null && message != null) {
		    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		    String strDate = sdf.format(new Date());
			
			JsonObject m = new JsonObject();
			m.addProperty("id", "chat");
			m.addProperty("date", strDate);
			m.addProperty("name", uname);
			m.addProperty("room", rname);
			m.addProperty("message", message);
			log.info("ROOM: {} user: {} chat message: {}", rname, uname, message);
			room.sendMessage(m);
			room.addChatMessage(m);
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		UserSession user = registry.removeBySession(session);
		if (user == null)
			return;
		Room room = user.getRoom();
		if (room == null)
			return;
		room.leave(user);
	}


	private void sendMessage2ALL(JsonObject msg) {
		for (UserSession u : registry.getUserSessions()) {
			try {
				u.sendMessage(msg);
			} catch (final IOException e) {
				log.debug("session with user {} could not be notified", u, e);
			}
		}
	}
	
	
	/*
	 * add empty room, notify registered participants
	 */
	private void addRoom(JsonObject params, UserSession user) throws IOException 
	{
		JsonElement je = params.get("name");
		if (null == je) {
			log.error("no 'room' parameter!");
			return;
		}

		String roomName = je.getAsString();
		ds.print("addRoom: %s", roomName);

		Room room = roomManager.getRoom(roomName);
		if (null != room) {
			/* already exist */
			log.info("room {} already exist!", roomName);
			user.sendError("room already exist: " + roomName);
			return;
		}

		/* create room */
		String roomDescr = params.get("descr").getAsString();
		String roomColor = params.get("color").getAsString();
 
		boolean r = roomManager.addRoom(roomName, roomDescr, roomColor);

		if (r) {
			/* notify users */
			JsonObject msg = new JsonObject();
			msg.addProperty("id", "addRoom");
			msg.addProperty("name", room.getName());
			msg.addProperty("descr", room.getDescr());
			msg.addProperty("color", room.getColorName());
			msg.addProperty("nusers", room.getUsersCount());
			sendMessage2ALL(msg);
			return;
		}
		
		user.sendError("cannot create room: " + roomName);
		return;

	}


	/**
	 * remove room, close room participants, notify other registered
	 * participants
	 */
	private void removeRoom(JsonObject params, UserSession user) throws IOException 
	{
		JsonElement je = params.get("name");
		if (null == je) {
			log.error("no 'room' parameter!");
			return;
		}

		String roomName = je.getAsString();
		ds.print("removeRoom: %s", roomName);

		Room room = roomManager.getRoom(roomName);
		if (null == room) {
			ds.print("room {} not exist!", roomName);
			log.error("room {} not exist!", roomName);
			user.sendError("room " + roomName + " dont exist");
			return;
		}

		roomManager.removeRoom(room);

		/* notify users */
		JsonObject msg = new JsonObject();
		msg.addProperty("id", "removeRoom");
		msg.addProperty("name", roomName);
		
		sendMessage2ALL(msg);
	}

	
	/*
	 * list rooms 4 given participant
	 */
	private void listRooms(JsonObject params, UserSession user) throws IOException {
		log.info("listRooms request");

		try {
			final JsonObject msg = new JsonObject();

			final JsonArray roomArray = new JsonArray();

			for (final Room room : roomManager.getRooms()) {
				JsonObject r = new JsonObject();
				r.addProperty("name", room.getName());
				r.addProperty("descr", room.getDescr());
				r.addProperty("color", room.getColorName());
				r.addProperty("nusers", room.getUsersCount());
				roomArray.add(r);
			}

			msg.addProperty("id", "listRooms");
			msg.add("data", roomArray);

			user.sendMessage(msg);

		} catch (Exception ex) {
			ds.error("Exception while sending list: " + ex.getMessage());
			ex.printStackTrace(System.err);
			user.sendError("exception while sending list");
		}

		return;

	}
}
