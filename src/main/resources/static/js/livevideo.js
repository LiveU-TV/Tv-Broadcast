/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: js/nextvideo.js
 * Purpose	: TODO
 * Author	: Slava Burykh
 * Created	: 20/09/2016
 */

const LIVE_VIDEO_CLASS = 'live';
const LIVE_VIDEO_ME_CLASS = 'live me';
const LIVE_VIDEO_ID = 'live_id';
const LIVE_VIDEO_CONTAINER = 'lives';

/**
 * Creates a video element for a live EP
 */
function LiveVideo(name) {
	console.log("creating live:'" + name + "'");

	this.name = name;

	this.container = document.createElement('div');
	this.container.className = LIVE_VIDEO_CLASS;
	if (name == loggedInUser) {
		this.container.className = LIVE_VIDEO_ME_CLASS;
	}
	this.container.id = LIVE_VIDEO_ID;
	this.span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

	this.container.appendChild(video);
	this.container.appendChild(this.span);
	
	document.getElementById(LIVE_VIDEO_CONTAINER).appendChild(this.container);
	
	this.span.appendChild(document.createTextNode(this.name));

	video.id = LIVE_VIDEO_ID + this.name + '-video';
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	}

	this.getVideoElement = function() {
		return video;
	}
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error");
		console.log('Invoking SDP offer callback function');
		var msg =  { id : "sdpOffer4Live",
				sender : this.name,
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate4Live',
		    name: this.name,
		    candidate: candidate,
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing live video');
		this.rtcPeer.dispose();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing live video');
	};
}