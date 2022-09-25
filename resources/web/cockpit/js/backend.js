document.addEventListener('DOMContentLoaded', (event) => {
    let webSocket = new ReconnectingWebSocket(
        (window.location.protocol === 'https:' ? 'wss://' : 'ws://')
        + window.location.host + '/ws/cockpit', null, {reconnectInterval: 500});
    let callbacks = [];
    let listeners = [];
    let socket = {};

    let registerCallback = function (requestId, callback) {
        // A callback isn't mandatory
        if (callback === undefined) {
            return;
        }
        if (callbacks[requestId]) {
            throw new Error(`A callback with the requestId '${requestId}' is already registered.`);
        }
        callbacks[requestId] = function (result) {
            try {
                callback(result.response);
            } catch (ex) {
                console.error(`Could not run callback for ${requestId}: ${ex}`);
                delete callbacks[requestId];
            }
        }
    }

    let sendToSocket = function (message) {
        try {
            let json = JSON.stringify(message);

            webSocket.send(json);

            // Make sure to not show the user's token.
            if (json.indexOf('authenticate') !== -1) {
                console.log('sendToSocket:: ' + json.substring(0, json.length - 20) + '.."}');
            } else {
                console.log('sendToSocket:: ' + json);
            }
        } catch (e) {
            console.error('Failed to send message to socket: ' + e.message);
        }
    };

    let handleAuth = function (message) {
        if (message.authresult === 'false') {
            console.error('Failed to auth with the socket.');
            toastr.error('Failed to auth with the socket.', '', {timeOut: 0});
        } else {
            console.log('Connection to backend established');
            document.dispatchEvent(new Event('backendConnected'));
        }
    };

    socket.subscribe = function (eventName, callback) {
        if (listeners[eventName] === undefined) {
            listeners[eventName] = [callback];
        } else {
            listeners[eventName].push(callback);
        }
    }

    webSocket.onmessage = function (e) {
        try {
            console.log('Message from socket: ' + e.data);
            if (e.data === 'PING') {
                webSocket.send('PONG');
                return;
            }
            let message = JSON.parse(e.data);

            if (message.authresult !== undefined) {
                handleAuth(message);
                return;
            }

            let callback = callbacks[message.requestId];
            let listener = listeners[message.requestId];

            if (callback !== undefined) {
                delete callbacks[message.requestId];
                callback(message);
            }
            if (listener !== undefined && listener.length > 0) {
                listener.forEach((eventCallback) => {
                    if (typeof eventCallback === 'function') {
                        eventCallback(message);
                    }
                })
            }
        } catch (ex) {
            console.error('Failed to parse message from socket: ' + ex + '\n' + ex.stack + '\n\n' + e.data);
        }
    };

    webSocket.onopen = function () {
      helpers.setupAuth(); // bugged
      auth = getAuth() || window.localStorage.getItem('webauth');
      if(auth === '!missing'){
        // Try again
        auth = window.localStorage.getItem('webauth');
      }
        sendToSocket({
            authenticate: auth
        });
    };

    webSocket.onclose = function () {
        console.log('Backend disconnected');
        document.dispatchEvent(new Event('backendDisconnected'));
    };

    socket.isClientPresent = function (requestId, callback) {
        registerCallback(requestId, callback);
        let requestType = 'isClientPresent';
        sendToSocket({requestId, requestType});
    };

    socket.getDbValue = function (requestId, table, key, callback) {
        registerCallback(requestId, callback);
        let requestType = 'getDBValue';
        sendToSocket({requestId, requestType, table, key});
    };

    // compatibility with helpers.js
    // thanks for setting a a stupid timeout when the file is just included
    socket.getDBValue = socket.getDbValue;

    socket.getDbValues = function (requestId, table, callback) {
        registerCallback(requestId, callback);
        let requestType = 'getDBValues';
        sendToSocket({requestId, requestType, table});
    };

    socket.setDbValue = function (requestId, table, key, value, callback) {
        registerCallback(requestId, callback);
        let requestType = 'setDBValue';
        sendToSocket({requestId, requestType, table, key, value});
    };

    socket.deleteDbKey = function (requestId, table, key, callback) {
        registerCallback(requestId, callback);
        let requestType = 'deleteDbKey';
        sendToSocket({requestId, requestType, table, key});
    }

    socket.playSound = function (requestId, filename, settings, callback) {
        registerCallback(requestId, callback);
        // Todo: Document Settings
        let requestType = 'playSound';
        sendToSocket({requestId, requestType, filename, settings});
    };

    socket.playClip = function (requestId, filename, settings, callback) {
        registerCallback(requestId, callback);
        // Todo: Document Settings
        let requestType = 'playClip';
        sendToSocket({requestId, requestType, filename, settings});
    };

    socket.playMacro = function (requestId, macroData, callback) {
        registerCallback(requestId, callback);
        // Todo: Document Settings
        let requestType = 'playMacro';
        sendToSocket({requestId, requestType, macroData: macroData});
    };

    socket.triggerEmote = function (requestId, emoteId, provider, amount, animationName, duration, ignoreSleep, callback) {
        registerCallback(requestId, callback);
        let requestType = 'triggerEmote';
        const data = {requestId, requestType, emoteId, provider, amount, ignoreSleep}
        if(animationName){
            data['animationName'] = animationName;
        }
        if(duration >= 0){
            data['duration'] = duration;
        }
        sendToSocket(data);
    };

    socket.getAudioFiles = function (requestId, callback) {
        registerCallback(requestId, callback);
        let requestType = 'getAudioFiles';
        sendToSocket({requestId, requestType});
    };

    socket.getClipFiles = function (requestId, callback) {
        registerCallback(requestId, callback);
        let requestType = 'getClipFiles';
        sendToSocket({requestId, requestType});
    };

    socket.stopMedia = function (requestId, type, callback) {
        registerCallback(requestId, callback);
        let requestType = 'stopMedia';
        sendToSocket({requestId, requestType, type});
    }

    socket.hexagon = function (requestId, enabled, username, callback) {
        registerCallback(requestId, callback);
        let requestType = 'hexagon';
        sendToSocket({requestId, requestType, enabled, username, callback});
    }

    socket.hexagonGameStart = function (requestId, playerName, level, callback){
        registerCallback(requestId, callback);
        let requestType = 'hexagonGameStart';
        sendToSocket({requestId, requestType, level, playerName});
    }
    socket.hexagonGameEnd = function (requestId, callback){
        registerCallback(requestId, callback);
        let requestType = 'hexagonGameEnd';
        sendToSocket({requestId, requestType});
    }
    socket.hexagonSetLevel = function (requestId, level, callback){
        registerCallback(requestId, callback);
        let requestType = 'hexagonSetLevel';
        sendToSocket({requestId, level, requestType});
    }

    // Make this a global object.
    window.socket = socket;
    document.dispatchEvent(new Event('backendInitialized'));
});