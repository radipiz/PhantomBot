let cockpit = {
    MAX_CHAT_ROWS: 10,
    DB_MACRO_TABLE: 'cockpitMacros',
    domChatrows: undefined,

    emotesBttv: [],
    emotesFfz: []
};

cockpit.loadEmoteCaches = function () {
    let closure = this;
    [
        {dbKey: 'ffzEmotes', target: 'emotesFfz'},
        {dbKey: 'bttvEmotes', target: 'emotesBttv'},
    ].forEach(providerDef => {
        socket.getDbValue('loadEmoteCache_' + providerDef.dbKey, 'emotecache', providerDef.dbKey, (result) => {
            let emotes = [];
            try {
                if (result.status === "OK") {
                    let providerEmotes = JSON.parse(result.value);
                    // Flatten the keys. No need to distinguish the source
                    Object.keys(providerEmotes).forEach((key) => {
                        emotes = emotes.concat(providerEmotes[key]);
                    });
                    // Replace the stored cache
                    closure[providerDef.target] = emotes;
                } else {
                    console.error(`Could not load ${providerDef.dbKey}: ${result.response.error}`);
                }
            } catch (ex) {
                console.error(`Could not parse data for ${providerDef.dbKey}: ${ex}`);
            }
        });
    });
}

cockpit.onChatMessage = function (event) {
    let parent = this.domChatrows.parentElement;
    let shouldScroll = parent.scrollTop + parent.clientHeight === parent.scrollHeight;
    let msgId = 'chatline-' + event.id;
    let msgTime = new Date(event.timestamp);
    let msgLi = document.createElement('li');
    let messageHtml = this.substituteExternalEmotes(this.substituteNativeEmotes(event.message, event.tags.emotes));
    msgLi.className = 'list-group-item';
    msgLi.id = msgId;
    msgLi.innerHTML = `<span class="me-1">${msgTime.getHours()}:${msgTime.getMinutes()}</span>
<span class="me-2">${event.sender}</span>
<span>${messageHtml}</span>`;
    this.domChatrows.append(msgLi);
    while (this.domChatrows.children.length > this.MAX_CHAT_ROWS) {
        this.domChatrows.children[0].remove();
    }
    if (shouldScroll) {
        parent.scrollTop = parent.scrollHeight;
    }
};

cockpit.substituteNativeEmotes = function (message, emoteString) {
    if (emoteString === undefined) {
        return message;
    }
    let emotesInMessage = emoteString.split('/');
    if (emotesInMessage.length === 0) {
        return message;
    }
    let emotes = [];
    emotesInMessage.forEach((emoteDesc) => {
        // Split the string into emoteId and occurrences
        let cutId = emoteDesc.split(':');
        // extract the emoteId
        let emoteId = cutId[0];
        // extract the positions, we only need one
        let positions = cutId[1].split(',', 1);
        // split the from-to description from any occurrence
        let fromAndTo = positions[0].split('-');
        // parse the indexes in the string to int
        let startIndex = parseInt(fromAndTo[0]);
        // add 1 to the end, to get the whole emote text
        let endIndex = parseInt(fromAndTo[1]) + 1;
        // extract the emote text to search for
        let emoteText = message.substring(startIndex, endIndex)

        // put an object to the stack with a term to search for and its replacement
        emotes.push({
            search: emoteText,
            replace: `<img src="https://static-cdn.jtvnw.net/emoticons/v1/${emoteId}/1.0" alt="${emoteText}" title="${emoteText}" />`
        });
    });
    // replace each emote in the message
    emotes.forEach(searchAndReplace => {
        message = message.replaceAll(searchAndReplace.search, searchAndReplace.replace);
    });
    return message;
};

cockpit.substituteExternalEmotes = function (message) {
    let modified = false;
    let messageParts = message.split(' ');
    [{
        getUrl: (emoteId) => `https://cdn.frankerfacez.com/emoticon/${emoteId}/1`,
        emotes: this.emotesFfz
    }, {
        getUrl: (emoteId) => `https://cdn.betterttv.net/emote/${emoteId}/1x`,
        emotes: this.emotesBttv
    }].forEach((provider) => {
        for (let i = 0; i < messageParts.length; i++) {
            provider.emotes.forEach((emote) => {
                if (messageParts[i] === emote.code) {
                    messageParts[i] = `<img src="${provider.getUrl(emote.id)}" alt="${emote.code}" title="${emote.code}"/>`;
                    modified = true;
                }
            });
        }
    });
    if (modified) {
        return messageParts.join(' ');
    }
    return message;
};

cockpit.reloadClips = function () {
    let closure = this;
    socket.getClipFiles('reloadClip', function (result) {
        closure.reloadMediaCallback('cliplist', result, 'clip');
    });
};

cockpit.reloadSounds = function () {
    let closure = this;
    socket.getAudioFiles('reloadSounds', function (result) {
        closure.reloadMediaCallback('soundlist', result, 'audio');
    });
};

cockpit.reloadMediaCallback = function (targetId, result, mediaType) {
    if (result.status === 'OK') {
        let data = result.files;
        if (mediaType === 'audio') {
            data = data.map(value => value.split('.').slice(0, -1).join('.'));
        }
        data = data.filter((value, index, self) => self.indexOf(value) === index);
        this.fillMediaList(targetId, data, mediaType);
    } else {
        console.error(`Could not reload ${targetId}: ${result.error}`);
    }
};

cockpit.reloadMacros = function () {
    let closure = this;
    socket.getDbValues('reloadMacros', this.DB_MACRO_TABLE, result => {
        closure.reloadMacrosCallback(result);
    });
};

cockpit.reloadMacrosCallback = function (result) {
    if (result.status !== 'OK') {
        console.error(result.error);
        return;
    }
    document.getElementById('macrolist').innerHTML = '';
    for (let i = 0; i < result.rows.length; i++) {
        this.addMacroToList(result.rows[i]);
    }
};

cockpit.addMacroToList = function (data) {
    let macroList = document.getElementById('macrolist');
    let macroData = JSON.parse(data.value);
    let li = document.createElement('li');
    li.className = 'list-group-item';
    li.dataset['macroid'] = data.key;
    li.innerHTML = `<button type="button" class="btn btn-secondary btn-sm me-2 btnPlayMacro">
        <i class="fa fa-play"></i>
    </button>
    <span class="title"></span>`;
    li.getElementsByClassName('title')[0].textContent = macroData.macroName;
    li.getElementsByClassName('btnPlayMacro')[0].addEventListener('click', event => {
        let macroId = event.currentTarget.parentElement.dataset['macroid'];
        if (macroId !== undefined) {
            socket.playMacro('playMacro-' + new Date().getTime(), data.value, result => {
                if (result.status !== "OK") {
                    toast('toast-error', 'Could not play macro: ' + result.error);
                }
            });
        }
    });
    macroList.append(li);
};

cockpit.fillMediaList = function (id, data, mediaType) {
    let ul = document.getElementById(id);
    data.sort()
    let newItems = [];
    data.forEach(item => {
        newItems.push(`<li class="list-group-item" data-filename="${item}">
                            <button type="button" class="btn btn-secondary btn-sm me-2">
                                <i class="fa fa-play"></i>
                            </button>
                            <span class="title">${item}</span>
                        </li>`)
    });
    ul.innerHTML = newItems.join('');

    for (let i = 0; i < ul.children.length; i++) {
        let targetFunction;
        switch (mediaType) {
            case 'audio':
                targetFunction = socket.playSound;
                break;
            case 'clip':
                targetFunction = socket.playClip;
                break;
            default:
                throw new Error('Unknown media type: ' + mediaType);
        }
        ul.children[i].dataset['filename'] = data[i];
        ul.children[i].addEventListener('click', event => {
            targetFunction(new Date().getTime().toString(), event.currentTarget.dataset['filename']);
        });
    }
};

cockpit.addEventHandlers = function () {
    let closure = this;
    document.getElementById('modeToggle-macros').addEventListener('click', event => {
        const newRowId = 'macros-rowNew';
        const existingNewButton = document.getElementById(newRowId);
        if (existingNewButton === null) {
            const newButtonId = 'macros-buttonNew';
            let li = document.createElement('li');
            li.className = 'list-group-item';
            li.id = newRowId;
            li.dataset['macroid'] = 'NEW';
            li.innerHTML = `<button type="button" id="${newButtonId}" class="btn btn-secondary btn-sm me-2">
                                <i class="fa fa-plus"></i>
                            </button>
                            <span class="title">New Macro</span>`;


            let macrolist = document.getElementById('macrolist');
            for (let i = 0; i < macrolist.children.length; i++) {
                let li = macrolist.children[i];
                let editButton = document.createElement('button');
                let deleteButton = document.createElement('button');
                editButton.type = deleteButton.type = 'button';
                const buttonClasses = ['btn', 'btn-secondary', 'btn-sm'];
                editButton.classList.add.apply(editButton.classList, buttonClasses);
                deleteButton.classList.add.apply(deleteButton.classList, buttonClasses);
                editButton.innerHTML = `<i class="fa fa-edit"/>`;
                deleteButton.innerHTML = `<i class="fa fa-trash"/>`;
                // Remove the whitespace characters
                if (li.childNodes[0].nodeType === Node.TEXT_NODE) {
                    li.childNodes[0].remove();
                }

                deleteButton.addEventListener('click', (event) => {
                    closure.openDeleteDialog(event, li.dataset['macroid'], li.getElementsByClassName('title')[0].textContent);
                });
                editButton.addEventListener('click', (event) => {
                    closure.openMacroEditor(event, li.dataset['macroid']);
                });
                li.prepend(editButton);
                li.prepend(deleteButton);
            }
            macrolist.prepend(li);
            document.getElementById(newButtonId).addEventListener('click', (event) => {
                MacroEditor.show(event);
            });
        } else {
            existingNewButton.remove();
            let macrolist = document.getElementById('macrolist');
            for (let i = 0; i < macrolist.children.length; i++) {
                let buttons = macrolist.children[i].getElementsByTagName('button');
                buttons[0].remove();
                buttons[0].remove();
            }
        }
    });
    document.getElementById('modalConfirmDeleteSubmit').addEventListener('click', (event) => {
        closure.submitDeleteMacroDialog(event);
    });

    document.getElementById('btnHexagonName').addEventListener('change', (event) => {
        let enabled = event.srcElement.value !== '';
        let username = event.srcElement.value;
        //socket.hexagon('hexagon', enabled, username, (result) => { console.log(result); });
    });
    document.getElementById('btnHexagonGameStart').addEventListener('click', (event) => {
        let playerName = document.getElementById('btnHexagonName').value;
        let level = parseInt(document.getElementById('inputHexagonLevel').value);
        socket.hexagonGameStart('hexagonGameStart', playerName, level, (result) => { console.log(result); })
    });
    document.getElementById('btnHexagonGameEnd').addEventListener('click', (event) => {
        socket.hexagonGameEnd('hexagonGameEnd', (result) => { console.log(result); })
    });
    document.getElementById('btnHexagonSetLevel').addEventListener('click', (event) => {
            let level = parseInt(document.getElementById('inputHexagonLevel').value);
            socket.hexagonSetLevel('hexagonSetLevel', level, (result) => { console.log(result); })
        });
}

cockpit.openMacroEditor = function (event, macroId) {
    if (macroId !== undefined) {
        socket.getDbValue('loadMacro', this.DB_MACRO_TABLE, macroId, (result) => {
            let macroData = JSON.parse(result.value);
            MacroEditor.show(event, macroId, macroData);
        });
    } else {
        MacroEditor.show(event, macroId);
    }
};

cockpit.handleSaveMacro = function () {
    let closure = this;
    let macroData = MacroEditor.exportMacro();
    let macroId = MacroEditor.getCurrentEditedId() || new Date().getTime().toString();
    let macroDataJson = JSON.stringify(macroData);
    console.log(macroData);
    console.log(macroId);
    socket.setDbValue('saveMacro', this.DB_MACRO_TABLE, macroId, macroDataJson, (result) => {
        if (result.status === 'OK') {
            toast('toast-success', `${macroData.macroName} has been saved.`);
            closure.reloadMacros();
        } else {
            toast('toast-error', `Could not save macro: ${result.error}`);
        }
    });
};

cockpit.openDeleteDialog = function (event, macroId, macroName) {
    document.getElementById('modalDeleteMacroName').innerText = macroName;
    let modalDiv = document.getElementById('modalConfirmDeleteMacro');
    modalDiv.dataset['macroid'] = macroId;
    let deleteConfirmModal = $(modalDiv);
    deleteConfirmModal.show();
};

cockpit.submitDeleteMacroDialog = function () {
    let macroName = document.getElementById('modalDeleteMacroName').innerText;
    let modalDiv = document.getElementById('modalConfirmDeleteMacro');
    let macroId = modalDiv.dataset['macroid'];
    let deleteConfirmModal = $(modalDiv);
    deleteConfirmModal.hide();
    socket.deleteDbKey('deleteMacro', this.DB_MACRO_TABLE, macroId, (result) => {
        if (result.status === 'OK') {
            toast('toast-success', `${macroName} has been deleted.`);
            let macroList = document.getElementById('macrolist');
            for (let i = 0; i < macroList.childElementCount; i++) {
                if (macroList.children[i].dataset['macroid'] === macroId) {
                    macroList.children[i].remove();
                    break;
                }
            }
        } else {
            toast('toast-error', `${result.error}`);
        }
    });
};

cockpit.getSoundfiles = function () {
    let result = [];
    let soundlist = document.getElementById('soundlist');
    for (let i = 0; i < soundlist.children.length; i++) {
        result.push(soundlist.children[i].dataset['filename']);
    }
    return result;
};

cockpit.getClipfiles = function () {
    let result = [];
    let cliplist = document.getElementById('cliplist');
    for (let i = 0; i < cliplist.children.length; i++) {
        result.push(cliplist.children[i].dataset['filename']);
    }
    return result;
};

cockpit.init = function () {
    let closure = this;
    this.domChatrows = document.getElementById("chatrows");
    socket.subscribe('ircChannelMessage', (event) => {
        closure.onChatMessage(event);
    });
    this.addEventHandlers();
    MacroEditor.init(function () {
        closure.handleSaveMacro();
    });

}

function toast(id, message) {
    let toastDiv = document.getElementById(id);
    toastDiv.getElementsByClassName('toast-body')[0].textContent = message;
    $(toastDiv).toast('show');
}

document.addEventListener('backendConnected', () => {
    console.log("Received backendConnected");
    cockpit.init();
    cockpit.loadEmoteCaches();
    cockpit.reloadClips();
    cockpit.reloadSounds();
    cockpit.reloadMacros();

    document.getElementById('btnStopMedia').addEventListener('click', () => {
        socket.stopMedia('stopMedia', 'all', () => {});
    });
});

document.addEventListener('backendDisconnected', () => {

});