/*
 * Copyright (C) 2016-2022 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

const app = {
    // Config entries
    enableAudioHooks: 'enableAudioHooks',
    audioHookVolume: 'audioHookVolume',
    enableFlyingEmotes: 'enableFlyingEmotes',
    enableGifAlerts: 'enableGifAlerts',
    gifAlertVolume: 'gifAlertVolume',
    enableVideoClips: 'enableVideoClips',
    videoClipVolume: 'videoClipVolume',
    enableDebug: 'enableDebug',

    // constants
    provider_twitch: 'twitch',
    provider_local: 'local',
    provider_maxcdn: 'maxcdn',
    provider_ffz: 'ffz',
    provider_bttv: 'bttv',

    // members
    webSocket: undefined,
    queryMap: undefined,
    isPlaying: false,
    isDebug: localStorage.getItem('phantombot_alerts_debug') === 'true' || false,
    queue: [],
    handleQueueInterval: undefined,
    queueProcessing: false,

    // playingAudioFiles
    playingAudioFiles: [],

    init: function () {
        let context = this;
        this.webSocket = this.getWebSocket();
        this.queryMap = this.getQueryMap();

        /*
         * @event Called once the socket opens.
         */
        this.webSocket.onopen = function () {
            context.printDebug('Successfully connected to the socket.', true);
            // Authenticate with the socket.
            context.sendToSocket({
                authenticate: getAuth()
            });

            // Handle processing the queue.
            context.handleQueueInterval = setInterval(function () {
                context.handleQueue();
            }, 5e2);
        };

        this.webSocket.onclose = function () {
            context.printDebug('Disconnected from the socket.', true);
            window.clearInterval(context.handleQueueInterval);
        };

        this.webSocket.onmessage = function (e) {
            try {
                let rawMessage = e.data;
                let message = JSON.parse(rawMessage);

                context.printDebug('[MESSAGE] ' + rawMessage);

                if (message.query_id === undefined) {
                    // Check for our auth result.
                    if (message.authresult !== undefined) {
                        if (message.authresult === 'true') {
                            context.printDebug('Successfully authenticated with the socket.', true);
                            // Handle this.
                            context.handleBrowserInteraction()
                        } else {
                            context.printDebug('Failed to authenticate with the socket.', true);
                        }
                    } else {
                        // Queue all events and process them one at-a-time.
                        context.queue.push(message);
                    }
                }
            } catch (ex) {
                context.printDebug('Failed to parse socket message [' + e.data + ']: ' + e.stack);
            }
        };

    },


    /*
     * @function Gets a new instance of the websocket.
     *
     * @return {ReconnectingWebSocket}
     */
    getWebSocket: function () {
        let socketUri = ((window.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host + '/ws/alertspolls'), // URI of the socket.
            reconnectInterval = 5000; // How often in milliseconds we should try reconnecting.

        return new ReconnectingWebSocket(socketUri, null, {
            reconnectInterval: reconnectInterval
        });
    },

    /*
     * @function Parses the query params in the URL and puts them into a map.
     *
     * @return {Map}
     */
    getQueryMap: function () {
        let queryString = window.location.search, // Query string that starts with ?
                queryParts = queryString.slice(1).split('&'), // Split at each &, which is a new query.
                queryMap = new Map(); // Create a new map for save our keys and values.

        for (let i = 0; i < queryParts.length; i++) {
            let key = queryParts[i].substring(0, queryParts[i].indexOf('=')),
                    value = queryParts[i].slice(queryParts[i].indexOf('=') + 1);

            if (key.length > 0 && value.length > 0) {
                queryMap.set(key, value);
            }
        }
        return queryMap;
    },

    /*
     * @function Prints debug logs.
     *
     * @param {String} message
     */
    printDebug: function (message, force) {
        if (this.isDebug || force) {
            console.log('%c[PhantomBot Log]', 'color: #6441a5; font-weight: 900;', message);
        }
    },

    /*
     * @function Toggles the debug mode.
     *
     * @param {String} toggle
     */
    window.toggleDebug = function (toggle) {
        localStorage.setItem('phantombot_alerts_debug', toggle.toString());

        // Refresh the page.
        window.location.reload();
    },
    /*
     * @function Checks if the query map has the option, if not, returns default.
     *
     * @param  {String} option
     * @param  {String} def
     * @return {String}
     */
    getOptionSetting: function (option, def) {
        if (this.queryMap.has(option)) {
            return this.queryMap.get(option);
        } else {
            return def;
        }
    },

    /*
     * @function Sends a message to the socket
     *
     * @param {String} message
     */
    sendToSocket: function (message) {
        try {
            this.webSocket.send(JSON.stringify(message));
        } catch (ex) {
            this.printDebug('Failed to send a message to the socket: ' + ex.stack);
        }
    },

    //Copied from https://davidwalsh.name/detect-supported-audio-formats-javascript
    supportsAudioType: function (type) {
        // Allow user to create shortcuts, i.e. just "mp3"
        let formats = {
            mp3: 'audio/mpeg',
            aac: 'audio/aac',
            ogg: 'audio/ogg; codecs="vorbis"'
        };
        let audio = document.createElement('audio');
        let ret = audio.canPlayType(formats[type] || type);

        if (this.getOptionSetting(this.enableDebug, 'false') === 'true') {
            $('.main-alert').append('<br />supportsAudioType(' + type + '): ' + ret);
        }

        this.printDebug('supportsAudioType(' + type + '): ' + ret);

        return ret;
    },

    //Copied from https://davidwalsh.name/detect-supported-video-formats-javascript
    supportsVideoType: function (type) {

        // Allow user to create shortcuts, i.e. just "webm"
        let formats = {
            ogg: 'video/ogg; codecs="theora"',
            ogv: 'video/ogg; codecs="theora"',
            webm: 'video/webm; codecs="vp8, vorbis"',
            mp4: 'video/mp4'
        };

        let video = document.createElement('video');
        let ret = video.canPlayType(formats[type] || type);

        if (getOptionSetting('show-debug', 'false') === 'true') {
            $('.main-alert').append('<br />supportsVideoType(' + type + '): ' + ret);
        }

        printDebug('supportsVideoType(' + type + '): ' + ret);

        return ret;
    },


    /*
     * @function Handles the user interaction for the page.
     */
    handleBrowserInteraction: function () {
        const audio = new Audio();

        // Try to play to see if we can interact.
        audio.play().catch(function (err) {
            // User need to interact with the page.
            if (err.toString().startsWith('NotAllowedError')) {
                let alert = document.createElement('button');
                alert.textContent = 'Click me to activate audio hooks';
                alert.id = 'browserInteractionButton';
                alert.addEventListener('click', function () {
                    this.remove();
                });
                document.body.append(alert);
            }
        });
    },

    /*
     * @function Handles the queue.
     */
    handleQueue: async function () {
        // Do not do anything if the queue is empty
        if (this.queueProcessing || this.queue.length === 0) {
            return;
        }
        this.queueProcessing = true;
        // Process the whole queue at once
        while (this.queue.length > 0) {
            let event = this.queue[0];
            let ignoreIsPlaying = (event.ignoreIsPlaying !== undefined ? event.ignoreIsPlaying : false);

            if (event === undefined) {
                console.error('Received event of type undefined. Ignoring.');
            } else if (event.emoteId !== undefined) {
                // do not respect isPlaying for emotes
                this.handleEmote(event);
            } else if (event.script !== undefined) {
                this.handleMacro(event);
            } else if (event.stopMedia !== undefined) {
                this.handleStopMedia(event);
            } else if (ignoreIsPlaying || this.isPlaying === false) {
                // sleep a bit to reduce the overlap
                await sleep(100);
                this.printDebug('Processing event: ' + JSON.stringify(event));
                // called method is responsible to reset this
                this.isPlaying = true;
                if (event.type === 'playVideoClip') {
                    this.handleVideoClip(event);
                } else if (event.alert_image !== undefined) {
                    this.handleGifAlert(event);
                } else if (event.audio_panel_hook !== undefined) {
                    this.handleAudioHook(event);
                } else {
                    this.printDebug('Received message and don\'t know what to do about it: ' + event);
                    this.isPlaying = false;
                }
            } else {
                // Event was not processed because something is already playing
                // Return to avoid dropping it
                this.queueProcessing = false;
                return;
            }
            // Remove the event
            this.queue.splice(0, 1);
        }
        this.queueProcessing = false;
    },

    handleStopMedia: function (json) {
        let stopVideo;
        let stopAudio;
        if (json.stopMedia === 'all') {
            stopVideo = stopAudio = true;
        } else {
            stopVideo = json.stopMedia.indexOf('video') >= 0;
            stopAudio = json.stopMedia.indexOf('audio') >= 0;
        }
        if (stopVideo) {
            let videoFrame = document.getElementById('main-video-clips');
            while (videoFrame.children.length > 0) {
                videoFrame.children[0].remove();
            }
        }
        if (stopAudio) {
            while (this.playingAudioFiles.length > 0) {
                this.playingAudioFiles[0].pause();
                this.playingAudioFiles[0].remove();
                this.playingAudioFiles.splice(0, 1);
            }
        }
        this.isPlaying = false;
    },

    /*
     * @function Checks for if the audio file exists since the socket doesn't pass the file type.
     *
     * @param  {String} name
     * @return {String}
     */
    getAudioFile: function (name, path) {
        let defaultPath = '/config/audio-hooks/';
        let fileName = '';
        let extensions = ['ogg', 'mp3', 'aac'];
        let found = false;

        if (path !== undefined) {
            defaultPath = path;
        }

        for (let x in extensions) {
            if (fileName.length > 0) {
                break;
            }
            if (this.supportsAudioType(extensions[x]) !== '') {
                found = true;
                $.ajax({
                    async: false,
                    method: 'HEAD',
                    url: defaultPath + name + '.' + extensions[x],
                    success: function () {
                        fileName = (defaultPath + name + '.' + extensions[x]);
                    }
                });
            }
        }

        if (!found) {
            this.printDebug(`Could not find a supported audio file for ${name}.`, true);
        }

        if (this.getOptionSetting(this.enableDebug, 'false') === 'true' && path === undefined) {
            $('.main-alert').append('<br />getAudioFile(' + name + '): Unable to find file in a supported format');
        }
        return fileName;
    },

    /*
     * @function Handles audio hooks.
     *
     * @param {Object} json
     */
    handleAudioHook: function (json) {
        // Make sure we can allow audio hooks.
        if (this.getOptionSetting(this.enableAudioHooks, 'false') === 'true') {
            let audioFile = this.getAudioFile(json['audio_panel_hook']);

            if (audioFile.length === 0) {
                this.printDebug('Invalid audio file in json', true);
                return;
            }

            // Create a new audio file.
            let audio = new Audio(audioFile);
            // Set the volume.
            audio.volume = this.getOptionSetting(this.audioHookVolume, '1');
            let context = this;
            audio.addEventListener('ended', function () {
                let instanceIndex = context.playingAudioFiles.indexOf(this);
                if (instanceIndex >= 0) {
                    context.playingAudioFiles.splice(instanceIndex, 1);
                }
                audio.currentTime = 0;
                context.isPlaying = false;
            });
            this.playingAudioFiles.push(audio);
            // Play the audio.
            audio.play().catch(function (err) {
                console.log(err);
            });
        } else {
            this.isPlaying = false;
        }
    },

    /**
     * Handles emote messages
     * @param json
     * @returns {Promise<void>}
     */
    handleEmote: async function (json) {
        let amount = json.amount !== undefined ? json.amount : 1;
        const animationName = json.animationName || 'flyUp';
        const duration = json.duration || 10000;
        const ignoreSleep = json.ignoreSleep || false;
        for (let i = 0; i < amount; i++) {
            this.displayEmote(json['emoteId'], json['provider'], animationName, duration);
            if(!ignoreSleep) {
                await sleep(getRandomInt(1, 200));
            }
        }
    },

    displayEmote: function (emoteId, provider, animationName, duration) {
        if (this.getOptionSetting(this.enableFlyingEmotes, 'false') === 'false') {
            // Feature not enabled, end the function
            return;
        }

        // scaling of the emote (by width)
        const size = 112;

        const browserSafeId = emoteId.replace(/\W/g, '');

        // a pseudo unique id to make sure, the keyframe names won't interfere each other
        const uniqueId = `${Date.now()}${Math.random().toString(16).substr(2, 8)}`

        let emoteUrl;
        switch (provider) {
            case this.provider_twitch:
                // Taken from the entry "emotes" on https://dev.twitch.tv/docs/irc/tags/#privmsg-twitch-tags
                emoteUrl = 'https://static-cdn.jtvnw.net/emoticons/v1/' + emoteId + '/3.0';
                break;
            case this.provider_local:
                emoteUrl = '/config/emotes/' + emoteId;
                break;
            case this.provider_maxcdn:
                emoteUrl = `https://twemoji.maxcdn.com/v/latest/svg/${emoteId}.svg`;
                break;
            case this.provider_bttv:
                emoteUrl = `https://cdn.betterttv.net/emote/${emoteId}/3x`;
                break;
            case this.provider_ffz:
                emoteUrl = `https://cdn.frankerfacez.com/emoticon/${emoteId}/4`;
                break;
            default:
                this.printDebug(`Could not find local emote '${emoteId}'`);
                return;
        }

        let emote = document.createElement('img');
        emote.style.position = 'absolute';
        emote.src = emoteUrl;
        emote.width = size;
        emote.id = `emote-${browserSafeId}-${uniqueId}`;
        emote.dataset['browserSafeId'] = browserSafeId;
        emote.dataset['uniqueId'] = uniqueId;

        emote = document.getElementById('main-emotes').appendChild(emote);
        if (animationName === 'flyUp') {
            this.emoteFlyingUp(emote);
        } else {
            this.emoteAnimated(emote, animationName, duration);
        }
    },

    emoteAnimated: function (emote, animationName, duration) {
        emote.style.top = getRandomInt(-5, 95) + 'vh';
        emote.style.left = getRandomInt(-5, 95) + 'vw';
        emote.classList.add('animatedEmote');
        emote.classList.add('animate__animated');
        emote.classList.add('animate__' + animationName);
        emote.classList.add('animate__infinite');

        setTimeout(() => {
            emote.remove();
        }, duration);
    },

    emoteFlyingUp: function (emote) {
        // How long should the emotes fly over the screen?
        const displayTime = 12 + Math.random() * 3;
        // How long should one side-way iteration take
        const sideWayDuration = 3 + Math.random();
        // How much distance may the side-way movements take
        // value is in vw (viewport width) -> screen percentage
        const sideWayDistance = 3 + getRandomInt(0, 20);
        // Spawn Range
        const spawnRange = getRandomInt(0, 80);

        const browserSafeId = emote.dataset['browserSafeId'];
        const uniqueId = emote.dataset['uniqueId'];
        const keyFrameFly = `emoteFly-${browserSafeId}-${uniqueId}`;
        const keyFrameSideways = `emoteSideWays-${browserSafeId}-${uniqueId}`;
        const keyFrameOpacity = `emoteOpacity-${browserSafeId}-${uniqueId}`;

        let emoteAnimation = new Keyframes(emote);

        Keyframes.define([{
            name: keyFrameFly,
            '0%': {transform: 'translate(' + spawnRange + 'vw, 100vh)'},
            '100%': {transform: 'translate(' + spawnRange + 'vw, 0vh)'}
        }]);

        Keyframes.define([{
            name: keyFrameSideways,
            '0%': {marginLeft: '0'},
            '100%': {marginLeft: sideWayDistance + 'vw'}
        }]);

        Keyframes.define([{
            name: keyFrameOpacity,
            '0%': {opacity: 0},
            '40%': {opacity: 1},
            '80%': {opacity: 1},
            '90%': {opacity: 0},
            '100%': {opacity: 0}
        }]);

        emoteAnimation.play([{
            name: keyFrameFly,
            duration: displayTime + 's',
            timingFunction: 'ease-in'
        }, {
            name: keyFrameSideways,
            duration: sideWayDuration + 's',
            timingFunction: 'ease-in-out',
            iterationCount: Math.round(displayTime / sideWayDuration),
            direction: 'alternate' + (getRandomInt(0, 1) === 0 ? '-reverse' : '')
        }, {
            name: keyFrameOpacity,
            duration: displayTime + 's',
            timingFunction: 'ease-in'
        }], {
            onEnd: (event) => {
                event.target.remove();
            }
        });
    },

    handleVideoClip: async function (json) {
        if (this.getOptionSetting(this.enableVideoClips, 'true') !== 'true') {
            return;
        }
        let context = this;
        let defaultPath = '/config/clips';
        let filename = json.filename;
        let duration = json.duration || -1;
        let fullscreen = json.fullscreen || false;
        let volume = this.getOptionSetting(this.videoClipVolume, '0.8');

        let video = document.createElement('video');
        video.src = `${defaultPath}/${filename}`
        video.autoplay = false;
        video.preload = 'auto';
        video.volume = volume;
        if (fullscreen) {
            video.className = 'fullscreen';
        }
        let frame = document.getElementById('main-video-clips');
        frame.append(video);

        video.play().catch(() => {
            console.error('Failed to play ' + video.src);
            context.isPlaying = false;
        });
        video.addEventListener('ended', (event) => {
            context.isPlaying = false;
            video.pause();
            video.remove();
        });
        if (duration > 0) {
            setTimeout(() => {
                video.pause();
                video.remove();
                context.isPlaying = false;
            }, duration);
        }
    },

    /**
     * Handles GIF alerts.
     *
     * @param {Object} json
     */
    handleGifAlert: async function (json) {
        // Make sure we can allow alerts.
        if (this.getOptionSetting(this.enableGifAlerts, 'true') === 'true') {
            let context = this;
            let defaultPath = '/config/gif-alerts/';
            let gifData = json['alert_image'];
            let gifDuration = 3000;
            let gifVolume = this.getOptionSetting(this.gifAlertVolume, '0.8');
            let gifFile = '';
            let gifCss = '';
            let gifText = '';
            let htmlObj;
            let audio;
            let isVideo = false;
            let hasAudio = false;

            // If a comma is found, that means there are custom settings.
            if (gifData.indexOf(',') !== -1) {
                let gifSettingParts = gifData.split(',');

                // Loop through each setting and set it if found.
                gifSettingParts.forEach(function (value, index) {
                    switch (index) {
                        case 0:
                            gifFile = value;
                            break;
                        case 1:
                            gifDuration = (parseInt(value) * 1000);
                            break;
                        case 2:
                            gifVolume = value;
                            break;
                        case 3:
                            gifCss = value;
                            break;
                        case 4:
                            gifText = value;
                            break;
                        default:
                            gifText = gifText + ',' + value;
                            break;
                    }
                });
            } else {
                gifFile = gifData;
            }

            // Check if the file is a gif, or video.
            if (gifFile.match(/\.(webm|mp4|ogg|ogv)$/) !== null) {
                htmlObj = $('<video/>', {
                    'src': defaultPath + gifFile,
                    'autoplay': 'false',
                    'style': gifCss,
                    'preload': 'auto'
                });

                htmlObj.prop('volume', gifVolume);
                isVideo = true;

                if (this.supportsVideoType(gifFile.substring(gifFile.lastIndexOf('.') + 1)) !== '') {
                    this.printDebug('Video format is not supported by the browser!', true);
                }
            } else {
                htmlObj = $('<img/>', {
                    'src': defaultPath + gifFile,
                    'style': gifCss,
                    'alt': "Video"
                });
            }

            let audioPath = this.getAudioFile(gifFile.slice(0, gifFile.indexOf('.')), defaultPath);

            if (audioPath.length > 0 && gifFile.substring(gifFile.lastIndexOf('.') + 1) !== audioPath.substring(audioPath.lastIndexOf('.') + 1)) {
                hasAudio = true;
                audio = new Audio(audioPath);
            }

            // p object to hold custom gif alert text and style
            let textObj = $('<p/>', {
                'style': gifCss
            }).text(gifText);

            await sleep(1000);

            // Append the custom text object to the page
            $('#alert-text').append(textObj).fadeIn(1e2).delay(gifDuration)
                .fadeOut(1e2, function () { //Remove the text with a fade out.
                    let t = $(this);

                    // Remove the p tag
                    t.find('p').remove();
                });

            // Append a new the image.
            $('#alert').append(htmlObj).fadeIn(1e2, function () {// Set the volume.
                if (isVideo) {
                    // Play the sound.
                    htmlObj[0].play().catch(function () {
                        // Ignore.
                    });
                }
                if (hasAudio) {
                    audio.volume = gifVolume;
                    audio.play().catch(function () {
                        // Ignore.
                    });
                }
            }).delay(gifDuration) // Wait this time before removing this image.
                .fadeOut(1e2, function () { // Remove the image with a fade out.
                    let t = $('#alert');

                    // Remove either the img tag or video tag.
                    if (!isVideo) {
                        // Remove the image.
                        t.find('img').remove();
                    } else {
                        // Remove the video.
                        t.find('video').remove();
                    }

                    if (hasAudio) {
                        // Stop the audio.
                        audio.pause();
                        // Reset the duration.
                        audio.currentTime = 0;
                    }
                    if (isVideo) {
                        htmlObj[0].pause();
                        htmlObj[0].currentTime = 0;
                    }
                    // Mark as done playing.
                    context.isPlaying = false;
                });
        } else {
            this.isPlaying = false;
        }
    },

    handleMacro: async function (json) {
        this.printDebug('Playing Macro: ' + json.macroName);
        for (let i = 0; i < json.script.length; i++) {
            let element = json.script[i];
            switch (element.elementType) {
                case 'clip':
                    this.isPlaying = true;
                    await this.handleVideoClip(element);
                    break;
                case 'emote':
                    element.emoteId = element.emoteId !== undefined ? element.emoteId.toString() : element.emotetext;
                    await this.handleEmote(element);
                    break;
                case 'pause':
                    await sleep(element.duration);
                    break;
                case 'sound':
                    await this.handleAudioHook({audio_panel_hook: element.filename, duration: element.duration});
                    break;
            }
        }
        this.printDebug('Finished playing macro: ' + json.macroName);
    }
}

$(function () {
    app.init();
});

/**
 * General purpose function to hold the execution for a moment
 * Usage: await sleep(1000)
 * @param ms time in milliseconds to sleep
 * @returns {Promise<unknown>} the Promise to wait for
 */
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * Generates a random number between the given min and max
 * @param min the minimum value
 * @param max the maximum value
 * @returns {number} a random number
 */
function getRandomInt(min, max) {
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

