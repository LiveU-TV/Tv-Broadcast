/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: RoomManager.java
 * Purpose	: Room Manager class  
 * Author	: Sergey K
 * Created	: 10/08/2016
 */

package liveu.tvbroadcast;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import redsoft.dsagent.*;


public class RoomManager {

	private final Logger log = LoggerFactory.getLogger(RoomManager.class);

	private final Ds ds = new Ds(getClass().getSimpleName());

//	@Autowired
	private KurentoClient kurento = KurentoClient.create();
	private MediaPipeline pipeline;

	private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();
	private TvbConnector tvbStudio;

	public RoomManager() throws Exception {
		try {
			pipeline = kurento.createMediaPipeline();
			
			if (Settings.TEST_MODE) {
				/* create test rooms */
				addRoom("Sport", "cycling, rowing", "Green");
				addRoom("Nature", "bears, horses, cats", "DarkSlateGray");
				addRoom("Tourism", "mounting", "DarkGoldenRod");
				addRoom("Administrator", "LIVE operators", "DarkBlue");
			}
		} catch (Exception e) {
			log.error("error creating RoomManager: {}", e);
			e.printStackTrace(System.err); // how to print it 2 ds?
			throw e;
		}
		ds.print(0, "RoomManager object created");
	}

	/**
	 * find room and return room
	 * 
	 * @return Room or null
	 */
	public Room getRoom(String roomName) {
		return this.rooms.get(roomName);
	}
	
	/**
	 * Create new room
	 * @param roomName
	 * 	the name of the room
	 * 
	 * @param roomDescr
	 *  description 4 room
	 *  
	 * @param roomColor
	 *  room color used in browser
	 *            
	 * @return true if room created, false if already exists
	 */
	public boolean addRoom(String roomName, String roomDescr, String roomColor) {
		if (this.rooms.containsKey(roomName)) {
			log.debug("Room {} already exists", roomName);
			return false;
		}
		log.info("Adding room {} descr {} color {}", roomName, roomDescr, roomColor);
		rooms.put(roomName, new Room(roomName, roomDescr, roomColor, pipeline));
		log.info("Room {} added", roomName);
		return true;
	}

	/**
	 * Removes a room from the list of available rooms.
	 *
	 * @param room
	 *            the room to be removed
	 */
	public void removeRoom(Room room) {
		this.rooms.remove(room.getName());
		room.close();
		log.info("{}: removed and closed", room);
	}

	/**
	 * get all rooms
	 */
	public Collection<Room> getRooms() {
		return rooms.values();
	}

}
