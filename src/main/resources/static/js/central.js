/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: js/central.js
 * Purpose	: Display central video
 * Author	: Slava Burykh
 * Created	: 22/09/2016
 */

const CENTRAL_VIDEO_CLASS = 'user';
const CENTRAL_VIDEO_ID = 'central_id';
const CENTRAL_VIDEO_CONTAINER = 'central';

/**
 * Creates a video element for central
 */
function CentralVideo() {
	console.log("creating central video");

	this.username = 'Central';
	
	this.container = document.createElement('div');
	this.container.className = CENTRAL_VIDEO_CLASS;
	this.container.id = CENTRAL_VIDEO_ID;
	this.span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

	this.container.appendChild(video);
	this.container.appendChild(this.span);
	
	document.getElementById(CENTRAL_VIDEO_CONTAINER).appendChild(this.container);
	
	this.span.appendChild(document.createTextNode(this.username));

	video.id = CENTRAL_VIDEO_ID + '-video';
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	}

	this.getVideoElement = function() {
		return video;
	}
	
	this.setUserName = function(name) {
		this.username = name;
		old = this.span;
		this.span = document.createElement('span');
		this.span.appendChild(document.createTextNode(this.username));		
		old.parentNode.replaceChild(this.span, old);
	}
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error");
		console.log('Invoking SDP offer callback function');
		var msg =  { id : "sdpOffer4Central",
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate4Central',
		    candidate: candidate,
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing central video');
		this.rtcPeer.dispose();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing central video');
	};
}