/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: js/nextvideo.js
 * Purpose	: TODO
 * Author	: Slava Burykh
 * Created	: 18/09/2016
 */

const NEXT_VIDEO_CLASS = 'user-video-next';
const NEXT_VIDEO_ID = 'video_next_id';
const NEXT_VIDEO_CONTAINER = 'video_next';

/**
 * Creates a video element for a new participant
 */
function NextVideo() {
	console.log("creating next video");

	this.username = 'not selected!';
	
	this.container = document.createElement('div');
	this.container.className = NEXT_VIDEO_CLASS;
	this.container.id = NEXT_VIDEO_ID;
	this.span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

	this.container.appendChild(video);
	this.container.appendChild(this.span);
	
	document.getElementById(NEXT_VIDEO_CONTAINER).appendChild(this.container);
	
	this.span.appendChild(document.createTextNode(this.username));

	video.id = NEXT_VIDEO_ID + '-video';
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
		var msg =  { id : "sdpOffer4NextVideo",
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate4NextVideo',
		    candidate: candidate,
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing next video');
		this.rtcPeer.dispose();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing next video');
	};
}