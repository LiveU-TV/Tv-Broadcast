/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: js/participant.js
 * Purpose	: TODO
 * Author	: Sergey K
 * Created	: 10/08/2016
 */

const PARTICIPANTS_CONTAINER = 'participants';
const PARTICIPANT_MAIN_CLASS = 'user main';
const PARTICIPANT_CLASS = 'user';
const PARTICIPANT_SELECTED_CLASS = 'user selected';
const PARTICIPANT_ME_CLASS = 'user me';
const PARTICIPANT_ME_SELECTED_CLASS = 'user me selected';


/**
 * Creates a video element for a new participant
 *
 * @param {String} name - the name of the new participant, to be used as tag
 *                        name of the video element.
 *                        The tag of the new element will be 'video<name>'
 * @return
 */
function Participant(name) {
	console.log("creating participant:'" + name + "'");

	this.name = name;
	this.geox = null;
	this.geoy = null;
	this.marker = null;
	this.selected = false;

	this.container = document.createElement('div');
	this.container.className = PARTICIPANT_CLASS;
	if (name == loggedInUser) {
		this.container.className = PARTICIPANT_ME_CLASS;
	}
	this.container.id = name;
	var span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

	var btn = document.createElement('input');
	btn.value = "Select";
	btn.type = "button";
	btn.id = "video_" + name + "_button";
	btn.className = "user-video-button";
	var that = this;
	btn.onclick = function() {
		that.requestSelection();
	};
	//.bind(this);
	
	
	/*function() {
		//onChooseUser(that.name, that.container);
		console.log('selecting user 4 next video: ' + that.name);
		this.
		var msg =  { id : "selectParticipant",
				name : that.name
			};
		sendMessage(msg);		
	};
	*/
	
	this.container.appendChild(video);
	this.container.appendChild(span);
	this.container.appendChild(btn);

	this.container.onmouseover = function() {
		btn.style.display = "block";
	}

	this.container.onmouseout = function() {
	    btn.style.display = 'none';
	}	
	
//container.onclick = switchContainerClass;

	//container.onclick = function() {
	//	onChooseUser(name, container);
	//};
	
	document.getElementById(PARTICIPANTS_CONTAINER).appendChild(this.container);
	
	span.appendChild(document.createTextNode(name));

	video.id = 'video-' + name;
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	}

	this.getVideoElement = function() {
		return video;
	}
	
	this.setGeoLocation = function(geox, geoy, markertype) {
		console.log("setLocation() user: " + this.name + " x: " + geox + " y: " + geoy + " marker: " + markertype);
		this.clearGeoLocation();
		this.geox = geox;
		this.geoy = geoy;
		this.marker = mapAddMarker(this.name, this.geox, this.geoy, markertype);
		if (this.marker !== null) {
			var that = this;
			this.marker.addListener('click',  function () {
				that.requestSelection();
			});
		}
	}

	this.clearGeoLocation = function() {
		if (this.marker != null) {
			this.marker.setMap(null);
			this.marker = null;
			this.geox = null;
			this.geoy = null;
		}
	}
	
	this.requestSelection = function() {
		console.log("requesting user selection: " + this.name);
		var msg = {
				id: 'selectParticipant',
				name: this.name
		};
		sendMessage(msg);		
	}
	
	this.setSelected = function(state) {
		if (state === true) {
			if (this.selected === true) return;

			this.setGeoLocation(this.geox, this.geoy, "selected");
			if (this.name == loggedInUser) {
				this.container.className = PARTICIPANT_ME_SELECTED_CLASS;				
			} else {
				this.container.className = PARTICIPANT_SELECTED_CLASS;
			}
			this.selected = true;
			return;
		}
		
		// else is false
		if (this.selected === false) return;
		if (this.name == loggedInUser) {
			this.setGeoLocation(this.geox, this.geoy, "self");
			this.container.className = PARTICIPANT_ME_CLASS;			
		} else {
			this.setGeoLocation(this.geox, this.geoy, "common");
			this.container.className = PARTICIPANT_CLASS;
		}
		this.selected = false;		
	}
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error")
		console.log('Invoking SDP offer callback function');
		var msg =  { id : "receiveVideoFrom",
				sender : name,
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate',
		    candidate: candidate,
		    name: name
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing participant ' + this.name);
		this.rtcPeer.dispose();
		this.clearGeoLocation();
		mapFitBounds();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing participant ' + this.name + ' DONE');
	};
}
