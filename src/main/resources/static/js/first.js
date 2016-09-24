/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: js/first.js
 * Purpose	: Init 1st page and request the Application server the room list  
 * Author	: Sergey K
 * Created	: 10/08/2016
 */

var loggedInUser = null;
var GeoX = 0;
var GeoY = 0;
var mapObject = null;

/* Global array of rooms */
var roomList = [ {
	name : "World",
	descr : "world news",
	color : "Crimson",
	nusers : 0
} ];

/* room element id prefix */
const
ROOM_ID_PREFIX = 'room_';

/* page GET parameters */
var GET_PARAMS = null;


/*
 * icons 4 marker
 */
var geoMarkers = {
	common : 'img/marker-dark-green2.png',
	selected : 'img/marker-red2.png',
	self : 'img/marker-bright-green2.png' };


/*
 * init websocket server connection
 *  uses global ws
 */
function initWS() {
	ws = new WebSocket('wss://' + location.host + '/groupcall');
	ws.onopen = wsOnOpen;
	ws.onclose = wsOnClose;
	ws.onmessage = wsOnMessage;
}

/*
 * Page startup
 */
function initFirst() {
	window.onerror = function(message, url, lineNumber) {
		alert("Uncatched error\n" +
		      "Message: " + message + "\n(" + url + ":" + lineNumber + ")");
	};
		  
	GET_PARAMS = getSearchParameters();
	if (GET_PARAMS['room'] != null) {
		page_2.style.display = "block";
		page_1.style.display = "none";

	} else {
		page_1.style.display = "block";
		page_2.style.display = "none";
	}
	page_0.style.display = "none";

	// btnJoinRoom.disabled = true;
	btnCreateRoom.disabled = true;

	// make websocket connection
	initWS();
	//roomList.forEach(function(item) {
	//	addRoom(item)
	//});
}

/*
 * get page params
 */
function getSearchParameters() {
	var prmstr = window.location.search.substr(1);
	return prmstr != null && prmstr != "" ? transformToAssocArray(prmstr) : {};
}

function transformToAssocArray(prmstr) {
	var params = {};
	var prmarr = prmstr.split("&");
	for (var i = 0; i < prmarr.length; i++) {
		var tmparr = prmarr[i].split("=");
		params[tmparr[0]] = tmparr[1];
	}
	return params;
}

function findRoomByName(roomName) {
	for (i = 0; i < roomList.length; i++) {
		if (roomName === roomList[i].name)
			return roomList[i];
	}
}

function findRoomById(roomId) {
	if (roomId.indexOf(ROOM_ID_PREFIX) != 0)
		return null;
	return findRoomByName(roomId.substr(ROOM_ID_PREFIX.length));
}

function addRoom(item) {
	var container = document.getElementById('roomList');
	container.appendChild(createRoomBox(item));
}

function updateRoom(item) {
	var container = document.getElementById('roomList');
	var elem = document.getElementById(ROOM_ID_PREFIX + item.name);
	newElem = createRoomBox(item);
	container.replaceChild(newElem, elem);
}

function createRoomBox(item) {
	var elem = document.createElement('div');
	elem.id = ROOM_ID_PREFIX + item.name;
	elem.className = "static-room static-room-active";
	elem.style.background = item.color;

	//var content = document.createElement('div');
	//content.className = "static-room-content";
	elem.innerHTML = "<b>" + item.name + "</b><br>" + item.descr;
	var nh = document.createElement('h3');
	nh.innerHTML = item.nusers;
//			+ "<br> Users: " + item.nusers + "<br>";
	var btn = document.createElement('input');
	btn.value = "Enter";
	btn.type = "button";
	btn.id = ROOM_ID_PREFIX + item.name + "_button";
	btn.className = "button-room";
	btn.onclick = function() {
		clickRoom(item.name);
	};
	
	elem.onmouseover = function() {
		loggedInUser != null ? btn.style.display = "block"
			: btn.style.display = "none";
	}

	elem.onmouseout = function() {
	    btn.style.display = 'none';
	}
	
	
	// var butcontent = document.createElement('div');
	// butcontent.style.textAlign = "center";
	// butcontent.appendChild(btn);
	// content.appendChild(butcontent);

	elem.appendChild(btn);
	elem.appendChild(nh);
//	elem.appendChild(content);
	return elem;
}

/* click on room panel */
function clickRoom(room) {
	var elem = document.getElementById(ROOM_ID_PREFIX + room);
	// var item = findRoomById(room);
	// var btn = document.getElementById(room + "_button");

	page_1.style.display = "none";
	page_2.style.display = "block";
	// room_header_2.innerHTML = item.name;
	register(loggedInUser, room);
	window.history.replaceState('test', '', "?room=" + room);
	// TODO
	mapInit();
}

/* login panel */
function showLoginPanel() {
	loginPanel.style.visibility = 'visible';
	loginPanel.style.left = ((document.body.clientWidth - 200) >> 1) + 'px';
	loginPanel.style.top = '200px';
	glassPanel.style.visibility = 'visible';
}

function clickLoginPanel() {
	// btnCreateRoom.disabled = false;

	sendMessage({
		id : 'login',
		login : login.value,
		password : null
	// login.password ?
	});
}

/*
 * called by incoming login event
 */
function afterLoginAttemp(item) {
	var ok = false;

	if (item.result != 'OK') {
		alert("Failed to login as: " + login.value + ", reason: "
				+ item.message);
	} else {
		ok = true;
		loggedInUser = item.login.replace(/['"]+/g, '');
		statusLogin.innerHTML = "Logged: " + loggedInUser;
		btnCreateRoom.disabled = false;
	}

	loginPanel.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';

	/* get user location */
	if (ok && navigator.geolocation) {
		navigator.geolocation.getCurrentPosition(saveGeoPosition);
	} else {
		console.log("unable get location!");
	}

}


/*
 * callback 4 geolocation
 */
function saveGeoPosition(position) {
	GeoY = position.coords.latitude;
	GeoX = position.coords.longitude;
	console.log("saveGeoPosition: (" + GeoX + "," + GeoY + ")");
	sendMessage({
		id : 'geo',
		geox : GeoX,
		geoy : GeoY
	});
}


/*
 * init Google map
 */
function mapInit() {
	console.log("mapInit()");
    //var coord = {lat: GeoY, lng: GeoX};
	mapObject = new google.maps.Map(document.getElementById('panel_map'), {
      zoom: 4,
      center: {lat: 40, lng: 30}
    });
}


/*
 * set Google map bounds from participants
 */
function mapFitBounds() {
	// bounds
	var b = {
	    north: 0,
	    south: 0,
	    east: 0,
	    west: 0
	};
	
	// is bound came?
	var c = {
		    north: false,
		    south: false,
		    east: false,
		    west: false			
	};
	
	// get bounds if any
	for (key in participants) {
		x = participants[key].geox;
		y = participants[key].geoy;
		
		if (x == 0 || y == 0) continue;
		
		// north
		if (c.north == false) {
			c.north = true; b.north = y;
		} else {
			b.north = Math.max(b.north,y);
		}
		
		// south
		if (c.south == false) {
			c.south = true; b.south = y;
		} else {
			b.south = Math.min(b.south,y);
		}

		// east
		if (c.east == false) {
			c.east = true; b.east = x;
		} else {
			b.east = Math.max(b.east,x);
		}
		
		// west
		if (c.west == false) {
			c.west = true; b.west = x;
		} else {
			b.west = Math.min(b.west,x);
		}		
	}
	
	if (b.east == b.west && b.north == b.south) {
		// only one point
		mapObject.setZoom(5);
		mapObject.setCenter(new google.maps.LatLng(b.south, b.east));
		return;
	} 

	if (c.east && c.west && c.north && c.south) {
		mapObject.fitBounds(b);
		//mapObject.panToBounds(b);
	}
}


/*
 * add market to Google map @mapObject
 */
function mapAddMarker(name, geox, geoy, markertype) {
	console.log("mapAddMarker: " + name + " (" + geox + "," + geoy + ")");
	console.log("geoMarker:" + geoMarkers['common']);
	if (geox == 0 || geoy == 0) {
		console.log("mapAddMarker: null coordinates!");
		return null;
	}
	
	var coord = {lat: geoy, lng: geox};
	
    return new google.maps.Marker({
      position: coord,
      map: mapObject,
      icon: geoMarkers[markertype],
      title: name,
      label: name[0].toUpperCase()
    });
}

function cancelLoginPanel() {
	loginPanel.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';
}

/* create new room */
function showCreateRoomPanel() {
	create_room.style.visibility = 'visible';
	create_room.style.left = ((document.body.clientWidth - 200) >> 1) + 'px';
	create_room.style.top = '200px';
	glassPanel.style.visibility = 'visible';
}

function clickCreateRoom() {
	create_room.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';

	sendMessage({
		id : 'addRoom',
		name : document.getElementById('newRoomName').value,
		descr : document.getElementById('newRoomDescr').value,
		color : 'Green'
	});
}

function cancelCreateRoom() {
	create_room.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';
}


/*
 * back 2 room list
 */
function showRoomListPage() {
	leaveRoom();
	page_1.style.display = "block";
	page_2.style.display = "none";
	window.history.replaceState('test', '', '?main=true');	
}

/*
 * send message 2 chat
 */
function clickSendChatMessage() {
	var msg = document.getElementById('chat_input').value;
	var chat = document.getElementById('chat_box');
	//chat.innerHTML += msg + "<br>";
	sendMessage({
		id : 'chat',
		message : msg
	});
	document.getElementById('chat_input').value = '';
	//chat.scrollTop = chat.scrollHeight;
}


/**
 * format chat message
 */
function formatChatMessage(r) {
	return "<b>" + r.date + "&nbsp;<font color=Blue>" + r.name + "</font></b>&nbsp;" + r.message + "<br>";
}

/**
 * add message 2 chat
 */
function addChatMessage(r) {
	var chat = document.getElementById('chat_box');
	chat.innerHTML += formatChatMessage(r);
	chat.scrollTop = chat.scrollHeight;
}


/**
 * set chat last messages
 * @param chat
 */
function setChat(data) {
	var chat = document.getElementById('chat_box');
	var cont = "";
	for (i = 0; i < data.length; i++) {
		cont += formatChatMessage(data[i]);
	}
	chat.innerHTML = cont;
	chat.scrollTop = chat.scrollHeight;
}

/**
 * OLD click on participant video
 */
function OLDonChooseUser(name, elem) {
	console.log("onChooseUser(): " + name);
	
	// clear selection
	for (key in participants) {
		if (participants[key].selected === true) {
			participants[key].setSelected(false);
		}		
	}
	// set user with name selected
	participants[name].setSelected(true);
	
	var old = document.getElementById('video_next');
	var n = document.createElement('div');
	n.id = "video_next";
	n.className = "user-video-next";
	
	// dublicate video
	var v = document.getElementById('video-' + name);
	var video = v.cloneNode(true);
	var span = document.createElement('span');
	span.appendChild(document.createTextNode(name));
	n.appendChild(video);
	n.appendChild(span);
	
	n.onclick = function() {
		var v = document.getElementById('video_next');
		var vtr = v.cloneNode(true);
		var ctr = document.getElementById('video_translation');		
		ctr.parentNode.replaceChild(vtr, ctr);
		vtr.id = "video_translation";
		page_2.style.display = "none";
		page_3.style.display = "block";
//		v.pause();
//		v.muted = true;
	};
	old.parentNode.replaceChild(n, old);
//	elem.pause();
//	elem.muted = true;

} 


/**
 * click on participant video
 */
function chooseUser(name) {
	console.log("chooseUser(): " + name);
	
	// clear selection
	for (key in participants) {
		if (participants[key].selected === true) {
			participants[key].setSelected(false);
		}		
	}
	// set user with name selected
	participants[name].setSelected(true);

	nextVideo.setUserName(name);
} 


/*
 * start live!
 */
function clickStartLive() {
	sendMessage({ id : 'startLive' });
}


/*
 * make live
 */
function activateLivePage() {
	page_2.style.display = "none";
	page_3.style.display = "block";
}


/*
 * stop live
 */
function backRoomPage() {
	sendMessage({ id: 'stopLive' });
	for (var key in lives)
		lives[key].dispose();
	
	lives = {};
	sendMessage({ id : 'disconnectCentral' });
	page_3.style.display = "none";
	page_2.style.display = "block";
}


/*
 * connect 2 central
 */
function connectCentral() {
	console.log("trying connect to Central");
	sendMessage({ id : 'connectCentral' });
	return;
//	
//	centralVideo = new CentralVideo();
//
//	var video = centralVideo.getVideoElement();
//
//	var options = {
//	     remoteVideo: video,
//	     onicecandidate: centralVideo.onIceCandidate.bind(centralVideo)
//	}
//
//	centralVideo.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
//		function (error) {
//		  if(error) {
//			  return console.error(error);
//		  }
//		  this.generateOffer (centralVideo.offerToReceiveVideo.bind(centralVideo));
//	});
}
