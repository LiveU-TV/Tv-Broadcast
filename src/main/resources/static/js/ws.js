/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: js/ws.js
 * Purpose	: Web Socket instance for communicating with Application Server. For usage by overlaying modules
 * Author	: Sergey K
 * Created	: 10/08/2016
 */

var ws = null;
var participants = {};
var lives = {}; // live participants
var nextVideo = null;
var centralVideo = null;
var name;


window.onbeforeunload = function() {
	ws.close();
};

function wsOnOpen() {
	console.info("WS connection on open");
	sendMessage({"id":"listRooms", "name":"ok"});
	//sendMessage({"id":"addRoom", "name":"Computers", "descr":"computers,etc", "color":"DarkCyan"});

};

function wsOnClose() {
	console.info("WS connection on close");
};

function wsOnMessage(message) {
	console.info(message);
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'error':
		onError(parsedMessage);
		break;
	case 'login':
		onLogin(parsedMessage);
		break;
	case 'listRooms':
		onListRooms(parsedMessage);
		break;
	case 'addRoom':
		onAddRoom(parsedMessage);
		break;
	case 'updateRoom':
		onUpdateRoom(parsedMessage);
		break;
	case 'chat':
		onChat(parsedMessage);
		break;
	case 'geo':
		onGeo(parsedMessage);
		break;
	case 'existingParticipants':
		onExistingParticipants(parsedMessage);
		break;
	case 'makeLive':
		makeLive(parsedMessage);
		break;
	case 'newParticipantArrived':
		onNewParticipant(parsedMessage);
		break;
	case 'participantLeft':
		onParticipantLeft(parsedMessage);
		break;
	case 'receiveVideoAnswer':
		onReceiveVideoAnswer(parsedMessage);
		break;
	case 'sdpAnswer4NextVideo':
		onSdpAnswer4NextVideo(parsedMessage);
		break;
	case 'sdpAnswer4Central':
		centralVideo.rtcPeer.processAnswer (parsedMessage.sdpAnswer, function (error) {
			if (error) return console.error (error);
		});	
		break;
	case 'sdpAnswer4Live':
		onSdpAnswer4Live(parsedMessage);
		break;
	case 'selectedParticipant':
		chooseUser(parsedMessage.name);
		break;
	case 'iceCandidate':
		if (participants.hasOwnProperty(parsedMessage.name)) {
			// if we waiting this...
			// fixed bug when room leaved (participants destroyed) and slow message came
			participants[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate: " + error);
					return;
				}
			});
		}
	    break;
	case 'iceCandidate4NextVideo':
		if (nextVideo != null) {
			nextVideo.rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate 4 next video: " + error);
					return;
				}
			});
		}
		break;
	case 'iceCandidate4Central':
		if (centralVideo != null) {
			centralVideo.rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate 4 central video: " + error);
					return;
				}
			});
		}
		break;
	case 'iceCandidate4Live':
		if (lives.hasOwnProperty(parsedMessage.name)) {
			// if we waiting this...
			// fixed bug when room leaved (participants destroyed) and slow message came
			lives[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate 4 live: " + error);
					return;
				}
			});
		}
	    break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
}


function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	// states CONNECTING OPEN CLOSING CLOSED

	if (ws.readyState !== ws.OPEN){
		console.log("WS ERROR:" + ws.readyState + " NOT SENDING!");
		return;
	}
	ws.send(jsonMessage);
}


function register(n, r) {
	name = n;
	room = r;
	console.log("Registering " + name + " in " + room);
	document.getElementById('room-header').innerHTML = 'News room: ' + room;
//	document.getElementById('room').style.display = 'block';

	var message = {
		id : 'joinRoom',
		name : name,
		room : room,
		geox : GeoX,
		geoy : GeoY,
	}

	sendMessage(message);
}


function onChat(r) {
	addChatMessage(r);
}

function onGeo(r) {
	var p = participants[r.name];
	if (p != null) {
		if (r.name == loggedInUser) {
			participants[r.name].setGeoLocation(r.geox, r.geoy, 'self');			
		} else {
			participants[r.name].setGeoLocation(r.geox, r.geoy, 'common');
		}
		mapFitBounds();
	}
}

function onListRooms(r) {
	for (i = 0; i < r.data.length; i++)
		addRoom(r.data[i]);
}

function onError(r) {
	console.log("Error: " + r.message);
	alert("Error: " + r.message)
}


function onAddRoom(r) {
	console.log("adding new room");
	addRoom({name : r.name, descr : r.descr, color : r.color, nusers : r.nusers});
}

function onUpdateRoom(r) {
	console.log("updating room: " + r.name);
	updateRoom({name : r.name, descr : r.descr, color : r.color, nusers : r.nusers});
}

function onLogin(r) {
	console.log("login done message");
	afterLoginAttemp(r);
}

/*
 * new participant arrive
 */
function onNewParticipant(r) {
	var participant = new Participant(r.name);
	participants[r.name] = participant;
	receiveVideo(participant);
	participant.setGeoLocation(r.geox, r.geoy, 'common');
	mapFitBounds();
}

function onReceiveVideoAnswer(result) {
	participants[result.name].rtcPeer.processAnswer (result.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});
}

/*
 * process SDP answer 4 next video
 */
function onSdpAnswer4NextVideo(r) {
	nextVideo.rtcPeer.processAnswer (r.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});	
}


/*
 * process SDP answer 4 live
 */
function onSdpAnswer4Live(result) {
	lives[result.name].rtcPeer.processAnswer (result.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});
}

//function callResponse(message) {
//	if (message.response != 'accepted') {
//		console.info('Call not accepted by peer. Closing call');
//		stop();
//	} else {
//		webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
//			if (error) return console.error (error);
//		});
//	}
//}

/*
 * get list of existing participants, create myself participant
 */
function onExistingParticipants(msg) {
	var constraints = {
		audio : true,
		video : {
			mandatory : {
				maxWidth : 300,
				maxFrameRate : 15,
				minFrameRate : 15
			}
		}
	};
	
	console.log(name + " registered in room " + room);

	//create self participant
	var participant = new Participant(name);

	// send geo coordinates
	sendMessage({
		id : 'geo',
		geox : GeoX,
		geoy : GeoY
	});

	/* set geo for myself */	
//	if (GeoX != null && GeoY != null) {
//		participant.setGeoLocation(GeoX, GeoY, 'self');
//	}
	
	participants[name] = participant;
	var video = participant.getVideoElement();

	var options = {
	      localVideo: video,
	      mediaConstraints: constraints,
	      onicecandidate: participant.onIceCandidate.bind(participant)
	    }
	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function (error) {
		  if(error) {
			  return console.error(error);
		  }
		  this.generateOffer (participant.offerToReceiveVideo.bind(participant));
	});

	// create other participants
	msg.data.forEach(function(item) {
		var participant = new Participant(item.name);
		participants[item.name] = participant;
		receiveVideo(participant);
		participant.setGeoLocation(item.geox, item.geoy, 'common');
	});
	
	// create next video
	nextVideo = new NextVideo();

	if (msg.hasOwnProperty('selected')) {
		chooseUser(msg.selected);
	}

	receiveNextVideo(nextVideo);
	
	mapFitBounds();
	setChat(msg.chat);
			
}

function leaveRoom() {
	sendMessage({
		id : 'leaveRoom'
	});

	for (var key in participants)
		participants[key].dispose();
	
	participants = {};
	
	if (nextVideo != null) {
		nextVideo.dispose();
		nextVideo = null;
	}

	//document.getElementById('room').style.display = 'none';
	document.getElementById('room-header').innerHTML = 'no active room!';
}

function receiveVideo(participant) {
	var video = participant.getVideoElement();

	var options = {
      remoteVideo: video,
      onicecandidate: participant.onIceCandidate.bind(participant)
    }

	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function (error) {
			  if(error) {
				  return console.error(error);
			  }
			  this.generateOffer (participant.offerToReceiveVideo.bind(participant));
	});
}

function receiveNextVideo(nv) {
	var video = nv.getVideoElement();

	var options = {
      remoteVideo: video,
      onicecandidate: nv.onIceCandidate.bind(nv)
    }

	nv.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function (error) {
			  if(error) {
				  return console.error(error);
			  }
			  this.generateOffer (nv.offerToReceiveVideo.bind(nv));
	});
}

function onParticipantLeft(request) {
	console.log('Participant ' + request.name + ' left');
	var participant = participants[request.name];
	participant.dispose();
	delete participants[request.name];
}

/*
 * get list of live participants, create myself live
 */
function makeLive(msg) {
	var constraints = {
		audio : true,
		video : {
			mandatory : {
				maxWidth : 300,
				maxFrameRate : 15,
				minFrameRate : 15
			}
		}
	};
	
	//create self live
	var live = new LiveVideo(name);
	
	lives[name] = live;
	var video = live.getVideoElement();

	var options = {
	      localVideo: video,
	      mediaConstraints: constraints,
	      onicecandidate: live.onIceCandidate.bind(live)
	    };
	
	live.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function (error) {
		  if(error) {
			  return console.error(error);
		  }
		  this.generateOffer (live.offerToReceiveVideo.bind(live));
	});

	// create other lives
	msg.data.forEach(function(item) {
		var live = new LiveVideo(item.name);
		lives[item.name] = live;
		receiveLiveVideo(live);
	});
	
	activateLivePage();
}


function receiveLiveVideo(live) {
	var video = live.getVideoElement();

	var options = {
      remoteVideo: video,
      onicecandidate: live.onIceCandidate.bind(live)
    };

	live.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function (error) {
			  if(error) {
				  return console.error(error);
			  }
			  this.generateOffer (live.offerToReceiveVideo.bind(live));
	});
}
