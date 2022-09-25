const ELEMENT_TYPE_CLIP = 'clip';
const ELEMENT_TYPE_EMOTE = 'emote';
const ELEMENT_TYPE_PAUSE = 'pause';
const ELEMENT_TYPE_SOUND = 'sound';

let MacroEditor = {
    attrFormsIds: [
        'macroEditor-attrPause',
        'macroEditor-attrEmote',
        'macroEditor-attrSound',
        'macroEditor-attrClip'
    ],
    editMacroModal: $(document.getElementById('modalEditMacro')),
    scriptList: document.getElementById('macroEditor-script'),
};

MacroEditor.hideAllAttrForms = function () {
    this.attrFormsIds.forEach(id => {
        let form = document.getElementById(id);
        if (form.dataset['elementIndex']) {
            delete form.dataset['elementIndex'];
        }
        form.classList.add('hide');
    });
};

MacroEditor.showAttrEditor = function (object) {
    this.hideAllAttrForms();
};

MacroEditor.createClipElement = function (data) {
    let li = document.createElement('li');
    li.dataset['elementType'] = ELEMENT_TYPE_CLIP;
    li.dataset['filename'] = data.filename || '';
    li.dataset['repeat'] = data.repeat || false;
    li.dataset['duration'] = data.duration || -1;
    return li;
};
MacroEditor.createEmoteElement = function (data) {
    let li = document.createElement('li');
    li.dataset['elementType'] = ELEMENT_TYPE_EMOTE;
    li.dataset['emotetext'] = data.emotetext || '';
    li.dataset['provider'] = data.provider || 'local';
    li.dataset['amount'] = data.amount || 1;
    li.dataset['duration'] = data.duration || 10000;
    li.dataset['animationname'] = data.animationName || '';
    li.dataset['ignoresleep'] = data.ignoreSleep || false;
    return li;
};
MacroEditor.createPauseElement = function (data) {
    let li = document.createElement('li');
    li.dataset['elementType'] = ELEMENT_TYPE_PAUSE;
    li.dataset['duration'] = data.duration || 0;
    return li;
};
MacroEditor.createSoundElement = function (data) {
    let li = document.createElement('li');
    li.dataset['elementType'] = ELEMENT_TYPE_SOUND;
    li.dataset['filename'] = data.filename || '';
    li.dataset['duration'] = data.duration || -1;
    return li;
};

MacroEditor.createMacroBlock = function (elementType, data) {
    data = data || {};
    let closure = this;
    let li;
    let iconClass;
    let playButtonAttributes = '';
    let playFunction;
    switch (elementType) {
        case ELEMENT_TYPE_CLIP:
            li = this.createClipElement(data);
            iconClass = 'fa-film';
            playFunction = socket.playClip;
            break;
        case ELEMENT_TYPE_EMOTE:
            li = this.createEmoteElement(data);
            iconClass = 'fa-comment';
            playFunction = socket.triggerEmote;
            break;
        case ELEMENT_TYPE_PAUSE:
            li = this.createPauseElement(data);
            iconClass = 'fa-pause';
            playButtonAttributes = 'disabled';
            break;
        case ELEMENT_TYPE_SOUND:
            li = this.createSoundElement(data);
            iconClass = 'fa-music';
            playFunction = socket.playSound;
            break;
        default:
            throw new Error('Unsupported elementType: ' + elementType);
    }
    li.classList.add('list-group-item');
    li.classList.add('list-group-item-action');
    li.innerHTML = `<button type="button" class="btn btn-secondary btn-sm deleteButton">
        <i class="fa fa-trash"></i>
    </button>
    <button type="button" class="btn btn-secondary btn-sm me-2 playButton" ${playButtonAttributes}>
        <i class="fa fa-play"></i>
    </button>
    <i class="fa ${iconClass} me-2"></i>
    <span class="title">Not set yet</span>`;

    li.addEventListener('click', event => {
        if (event.target.tagName !== 'LI') {
            return;
        }
        for (let i = 0; i < closure.scriptList.children.length; i++) {
            if (closure.scriptList.children[i] === event.currentTarget) {
                closure.editMacroBlock(i);
                return;
            }
        }
        throw new Error('Could not find macro block in script list');
    });
    li.getElementsByClassName('deleteButton')[0].addEventListener('click', event => {
        let elementIndex = -1;
        for (let i = 0; i < closure.scriptList.children.length; i++) {
            if (closure.scriptList.children[i] === event.currentTarget.parentElement) {
                elementIndex = i;
                break;
            }
        }
        if (elementIndex < 0) {
            throw new Error('Could not find element in macroList');
        }
        elementIndex = elementIndex.toString();
        this.attrFormsIds.forEach(id => {
            let form = document.getElementById(id);
            if (form.dataset['elementIndex'] === elementIndex) {
                delete form.dataset['elementIndex'];
                form.classList.add('hide');
            }
        });
        event.currentTarget.parentElement.remove();
    });
    if (playFunction !== undefined) {
        let playButton = li.getElementsByClassName('playButton')[0];
        let playCallback;
        let requestId = 'macroEditorPlay-' + new Date().getTime();
        if (playFunction === socket.triggerEmote) {
            playCallback = event => {
                let emoteText = li.dataset['emotetext'];
                let provider = li.dataset['provider'];
                let amount = li.dataset['amount'];
                let animationName = li.dataset['animationname'];
                let ignoreSleep = li.dataset['ignoresleep'] === 'true';
                let duration = li.dataset['duration'];
                let emoteId = closure.translateEmoteToId(emoteText, provider);
                playFunction(requestId, emoteId, provider, amount, animationName, duration, ignoreSleep, () => {
                });
            };
        } else {
            playCallback = event => {
                let filename = li.dataset['filename'];
                let data = {
                    duration: li.dataset['duration'],
                    repeat: li.dataset['repeat']
                };
                playFunction(requestId, filename, data, () => {
                });
            };
        }
        playButton.addEventListener('click', playCallback);
    }

    this.storeDataInBlock(li, data);
    return li;
};

MacroEditor.addNewMacroBlock = function (elementType) {
    let macroBlock = this.createMacroBlock(elementType);
    this.scriptList.children[this.scriptList.children.length - 1].insertAdjacentElement('beforebegin', macroBlock);
    this.editMacroBlock(this.scriptList.children.length - 2);
    this.updateSaveButtonDisabled();
};

MacroEditor.displayClipAttr = function (elementIndex, data) {
    let form = document.getElementById('macroEditor-attrClip');
    let selectFilename = document.getElementById('macroEditor-clip');
    let checkboxRepeat = document.getElementById('macroEditor-clipRepeat');
    let inputDuration = document.getElementById('macroEditor-clipDuration');
    let clipFiles = cockpit.getClipfiles();
    this.createOptions(selectFilename, clipFiles);

    form.dataset['elementIndex'] = elementIndex;
    form.classList.remove('hide');

    this.setOptionAsSelected(selectFilename, data.filename);
    checkboxRepeat.checked = data.repeat === 'true' || data.repeat === true;
    inputDuration.value = data.duration;
};
MacroEditor.displayEmoteAttr = function (elementIndex, data) {
    let form = document.getElementById('macroEditor-attrEmote');
    let selectProvider = document.getElementById('macroEditor-emoteProvider');
    let inputEmoteText = document.getElementById('macroEditor-emoteText');
    let inputAmount = document.getElementById('macroEditor-emoteAmount');
    let selectAnimation = document.getElementById('macroEditor-emoteAnimation');
    let inputDuration = document.getElementById('macroEditor-emoteDuration');
    let inputIgnoreSleep = document.getElementById('macroEditor-emoteIgnoreSleep');

    form.dataset['elementIndex'] = elementIndex;
    form.classList.remove('hide');

    this.setOptionAsSelected(selectProvider, data.provider);
    inputEmoteText.value = data.emotetext;
    inputAmount.value = data.amount;
    this.setOptionAsSelected(selectAnimation, data.animationName || "");
    inputDuration.value = data.duration || 10000;
    inputIgnoreSleep.checked = data.ignoreSleep || false;
    this.validateEmote();
};
MacroEditor.displayPauseAttr = function (elementIndex, data) {
    let form = document.getElementById('macroEditor-attrPause');
    let inputDuration = document.getElementById('macroEditor-pauseDuration');

    form.dataset['elementIndex'] = elementIndex;
    form.classList.remove('hide');

    inputDuration.value = data.duration;
};
MacroEditor.displaySoundAttr = function (elementIndex, data) {
    let form = document.getElementById('macroEditor-attrSound');
    let selectFilename = document.getElementById('macroEditor-soundFile');
    let inputDuration = document.getElementById('macroEditor-soundDuration');
    let soundFiles = cockpit.getSoundfiles()
    this.createOptions(selectFilename, soundFiles);

    form.dataset['elementIndex'] = elementIndex;
    form.classList.remove('hide');

    this.setOptionAsSelected(selectFilename, data.filename);
    inputDuration.value = data.duration;
};

MacroEditor.saveClipAttr = function (macroBlock) {
    let selectFilename = document.getElementById('macroEditor-clip');
    let checkboxRepeat = document.getElementById('macroEditor-clipRepeat');
    let inputDuration = document.getElementById('macroEditor-clipDuration');

    let data = {
        elementType: ELEMENT_TYPE_CLIP,
        filename: selectFilename.children[selectFilename.selectedIndex].value,
        repeat: checkboxRepeat.checked,
        duration: inputDuration.value
    };
    this.storeDataInBlock(macroBlock, data);
};
MacroEditor.saveEmoteAttr = function (macroBlock) {
    let selectProvider = document.getElementById('macroEditor-emoteProvider');
    let inputEmoteText = document.getElementById('macroEditor-emoteText');
    let inputAmount = document.getElementById('macroEditor-emoteAmount');
    let inputEmoteId = document.getElementById('macroEditor-emoteId');
    let selectAnimation = document.getElementById('macroEditor-emoteAnimation');
    let inputDuration = document.getElementById('macroEditor-emoteDuration');
    let inputIgnoreSleep = document.getElementById('macroEditor-emoteIgnoreSleep');

    let data = {
        elementType: ELEMENT_TYPE_EMOTE,
        provider: selectProvider.children[selectProvider.selectedIndex].value,
        emotetext: inputEmoteText.value,
        emoteid: inputEmoteId.value,
        amount: inputAmount.value,
        duration: inputDuration.value,
        ignoreSleep: inputIgnoreSleep.checked
    };
    const animationName = selectAnimation.children[selectAnimation.selectedIndex].value;
    if (animationName) {
        data.animationName = animationName;
    }
    this.storeDataInBlock(macroBlock, data);
};
MacroEditor.savePauseAttr = function (macroBlock) {
    let inputDuration = document.getElementById('macroEditor-pauseDuration');

    let data = {
        elementType: ELEMENT_TYPE_PAUSE,
        duration: inputDuration.value
    };
    this.storeDataInBlock(macroBlock, data);
};
MacroEditor.saveSoundAttr = function (macroBlock) {
    let selectFilename = document.getElementById('macroEditor-soundFile');
    let inputDuration = document.getElementById('macroEditor-soundDuration');

    let data = {
        elementType: ELEMENT_TYPE_SOUND,
        filename: selectFilename.children[selectFilename.selectedIndex].value,
        duration: inputDuration.value
    };
    this.storeDataInBlock(macroBlock, data);
};

MacroEditor.translateEmoteToId = function (code, provider) {
    let emoteId = null;
    let emoteSet = [];
    switch (provider) {
        case 'bttv':
            emoteSet = cockpit.emotesBttv;
            break;
        case 'ffz':
            emoteSet = cockpit.emotesFfz;
            break;
        case 'twitch':
            // not supported yet
            break;
    }
    for (let i = 0; i < emoteSet.length; i++) {
        if (code === emoteSet[i].code) {
            emoteId = emoteSet[i].id;
            break;
        }
    }
    return emoteId;
};

MacroEditor.translateEmoteIdToCode = function (id, provider) {
    let emoteCode = null;
    let emoteSet = [];
    switch (provider) {
        case 'bbtv':
            emoteSet = cockpit.emotesBttv;
            break;
        case 'ffz':
            emoteSet = cockpit.emotesFfz;
            break;
        case 'twitch':
            // not supported yet
            break;
    }
    for (let i = 0; i < emoteSet.length; i++) {
        if (id === emoteSet[i].id) {
            emoteCode = emoteSet[i].code;
            break;
        }
    }
    return emoteCode;
};

MacroEditor.editMacroBlock = function (elementIndex) {
    let macroBlock = this.scriptList.children[elementIndex];
    let data = this.extractDataFromBlock(macroBlock);
    this.hideAllAttrForms();
    switch (data.elementType) {
        case ELEMENT_TYPE_CLIP:
            this.displayClipAttr(elementIndex, data);
            break;
        case ELEMENT_TYPE_EMOTE:
            this.displayEmoteAttr(elementIndex, data);
            break;
        case ELEMENT_TYPE_PAUSE:
            this.displayPauseAttr(elementIndex, data);
            break;
        case ELEMENT_TYPE_SOUND:
            this.displaySoundAttr(elementIndex, data);
            break;
    }
};

MacroEditor.extractDataFromBlock = function (macroBlock) {
    let data = {
        elementType: macroBlock.dataset['elementType']
    };
    switch (macroBlock.dataset['elementType']) {
        case ELEMENT_TYPE_CLIP:
            data.filename = macroBlock.dataset['filename'];
            data.repeat = macroBlock.dataset['repeat'];
            data.duration = macroBlock.dataset['duration'];
            break;
        case ELEMENT_TYPE_EMOTE:
            data.emoteId = macroBlock.dataset['emoteid'];
            data.emotetext = macroBlock.dataset['emotetext'];
            data.provider = macroBlock.dataset['provider'];
            data.amount = macroBlock.dataset['amount'];
            if (macroBlock.dataset['duration'] > 0) {
                data.duration = parseInt(macroBlock.dataset['duration'])
            }
            if (macroBlock.dataset['animationname'] !== undefined && macroBlock.dataset['animationname']) {
                data.animationName = macroBlock.dataset['animationname'];
            }
            data.ignoreSleep = macroBlock.dataset['ignoresleep'] === 'true';
            break;
        case ELEMENT_TYPE_PAUSE:
            data.duration = macroBlock.dataset['duration'];
            break;
        case ELEMENT_TYPE_SOUND:
            data.filename = macroBlock.dataset['filename'];
            data.duration = macroBlock.dataset['duration'];
    }
    return data;
};

MacroEditor.storeDataInBlock = function (macroBlock, data) {
    if (data.elementType !== undefined && macroBlock.dataset['elementType'] !== data.elementType) {
        throw new Error(`MacroBlock is not of the same elementType as the data: Expected '${data.elementType}', got '${macroBlock.dataset['elementType']}'`);
    }
    let displayText;
    switch (data.elementType) {
        case ELEMENT_TYPE_CLIP:
            macroBlock.dataset['filename'] = data.filename;
            macroBlock.dataset['repeat'] = data.repeat;
            macroBlock.dataset['duration'] = data.duration;
            displayText = data.filename
                + (data.duration > 0 ? ` (${data.duration}ms)` : '')
                + (data.repeat ? ' [looped]' : '');
            break;
        case ELEMENT_TYPE_EMOTE:
            let emoteId = this.translateEmoteToId(data.emotetext, data.provider);
            macroBlock.dataset['emotetext'] = data.emotetext;
            macroBlock.dataset['provider'] = data.provider;
            macroBlock.dataset['amount'] = data.amount;
            if (data.duration > 0) {
                macroBlock.dataset['duration'] = data.duration;
            }
            if (data.animationName !== undefined && data.animationName) {
                macroBlock.dataset['animationname'] = data.animationName;
            }else{
                delete macroBlock.dataset['animationname'];
            }
            macroBlock.dataset['ignoresleep'] = data.ignoreSleep;
            displayText = `${data.amount}x ${data.emotetext} [${data.provider}]`;

            let playButton = macroBlock.getElementsByClassName('playButton')[0];
            if (emoteId !== null) {
                macroBlock.dataset['emoteid'] = emoteId;
                playButton.disabled = false;
            } else {
                delete macroBlock.dataset['emoteid'];
                playButton.disabled = true;
            }
            break;
        case ELEMENT_TYPE_PAUSE:
            macroBlock.dataset['duration'] = data.duration;
            displayText = `Pause: ${data.duration} ms`;
            break;
        case ELEMENT_TYPE_SOUND:
            macroBlock.dataset['filename'] = data.filename;
            macroBlock.dataset['duration'] = data.duration;
            displayText = data.filename + (data.duration > 0 ? ` (${data.duration}ms)` : '');
            break;
    }
    if (displayText !== undefined) {
        macroBlock.getElementsByClassName('title')[0].innerText = displayText;
        macroBlock.dataset['state'] = 'valid';
    } else {
        macroBlock.getElementsByClassName('title')[0].innerText = 'Configuration Missing';
        macroBlock.dataset['state'] = 'initial';
    }
    this.updateSaveButtonDisabled();
}

MacroEditor.newElementButtonPressed = function (event) {
    switch (event.target.dataset['element']) {
        case ELEMENT_TYPE_CLIP:
        case ELEMENT_TYPE_EMOTE:
        case ELEMENT_TYPE_PAUSE:
        case ELEMENT_TYPE_SOUND:
            this.addNewMacroBlock(event.target.dataset['element']);
            break;
    }
};

MacroEditor.saveAttrPressed = function (event) {
    let form = event.currentTarget.parentElement.parentElement;
    let elementIndex = parseInt(form.dataset['elementIndex']);
    let macroBlock = this.scriptList.children[elementIndex];
    let elementType = macroBlock.dataset['elementType'];
    switch (elementType) {
        case ELEMENT_TYPE_CLIP:
            this.saveClipAttr(macroBlock);
            break;
        case ELEMENT_TYPE_EMOTE:
            this.saveEmoteAttr(macroBlock);
            break;
        case ELEMENT_TYPE_PAUSE:
            this.savePauseAttr(macroBlock)
            break;
        case ELEMENT_TYPE_SOUND:
            this.saveSoundAttr(macroBlock);
            break;
    }
};

MacroEditor.addAppendButtons = function () {
    let closure = this;
    let li = document.createElement('li');
    li.className = 'list-group-item';
    li.innerHTML = `<div class="btn-group" role="group" aria-label="Add Macroblock">
    <button type="button" class="btn btn-secondary" id="macroEditor-button-pause" data-element="pause">Pause</button>
    <button type="button" class="btn btn-secondary" id="macroEditor-button-emote" data-element="emote">Emote </button>
    <button type="button" class="btn btn-secondary" id="macroEditor-button-sound" data-element="sound">Sound</button>
    <button type="button" class="btn btn-secondary"id="macroEditor-button-clip" data-element="clip">Clip</button>
    </div>`;
    this.scriptList.append(li);
    [
        'macroEditor-button-pause',
        'macroEditor-button-emote',
        'macroEditor-button-sound',
        'macroEditor-button-clip'
    ].forEach(id => {
        document.getElementById(id).addEventListener('click', (event) => {
            closure.newElementButtonPressed(event);
        });
    });
};

MacroEditor.loadMacro = function (data) {
    document.getElementById('macroEditor-macroName').value = data.macroName;
    data.script.forEach(element => {
        let macroBlock = this.createMacroBlock(element.elementType, element);
        this.scriptList.children[this.scriptList.children.length - 1].insertAdjacentElement('beforebegin', macroBlock);
    });
}

MacroEditor.exportMacro = function () {
    let macroName = document.getElementById('macroEditor-macroName').value.trim();
    let script = [];
    // Dump all element in the list minus the last row with the buttons
    for (let i = 0; i < this.scriptList.children.length - 1; i++) {
        if (this.scriptList.children[i].dataset['state'] !== 'valid') {
            throw new Error('Cannot save block without values');
        }
        script.push(this.extractDataFromBlock(this.scriptList.children[i]));
    }
    return {macroName, script};
};

MacroEditor.updateSaveButtonDisabled = function () {
    let saveButton = document.getElementById('modalEditMacroSubmit');
    for (let i = 0; i < this.scriptList.children.length - 1; i++) {
        if (this.scriptList.children[i].dataset['state'] !== 'valid') {
            saveButton.disabled = true;
            return;
        }
    }
    saveButton.disabled = false;
};

MacroEditor.dispose = function () {
    this.editMacroModal.hide();
    let modalDiv = this.editMacroModal.unwrap()[0];
    document.getElementById('macroEditor-script').innerHTML = '';
    delete modalDiv.dataset['macroid'];
};

MacroEditor.show = function (event, macroId, data) {
    let titleText = 'Create Macro';
    let modalDiv = this.editMacroModal.unwrap()[0];
    this.addAppendButtons();
    if (data !== undefined) {
        modalDiv.dataset['macroid'] = macroId;
        this.loadMacro(data);
        titleText = 'Edit Macro';
    }
    document.getElementById('modalEditMacroTitle').innerText = titleText;
    this.editMacroModal.show({
        backdrop: 'static',
        keyboard: false,
        focus: true
    });
    this.hideAllAttrForms();
};

MacroEditor.getCurrentEditedId = function () {
    return this.editMacroModal.unwrap()[0].dataset['macroid'];
}

MacroEditor.createOptions = function (target, options) {
    target.innerHTML = '';
    options.forEach(value => {
        let option = document.createElement('option');
        option.value = value;
        option.innerText = value;
        target.appendChild(option);
    });
};

MacroEditor.setOptionAsSelected = function (select, optionValue) {
    let options = select.getElementsByTagName('option');
    for (let i = 0; i < options.length; i++) {
        options[i].selected = options[i].value === optionValue;
    }
};

MacroEditor.validateEmote = function (event) {
    let target = event !== undefined ? event.currentTarget : document.getElementById('macroEditor-emoteText');
    let providerSelect = document.getElementById('macroEditor-emoteProvider');
    let provider = providerSelect.options[providerSelect.selectedIndex].value;
    let emoteId = this.translateEmoteToId(target.value, provider);
    let emoteIdField = document.getElementById('macroEditor-emoteId');
    let providerSupported = (provider === 'ffz' || provider === 'bttv');
    let validationIndicator = document.getElementById('macroEditor-emoteText-status');
    if (typeof emoteId === 'string') {
        emoteIdField.value = emoteId;
        validationIndicator.className = 'fa fa-check-circle';
        validationIndicator.title = 'Validation succeeded! The emote should work!';
    } else {
        emoteIdField.value = '';
        if (providerSupported) {
            validationIndicator.className = 'fa fa-times-circle';
            validationIndicator.title = 'Validation failed! It is likely, that the emote shows up as broken image';
        } else {
            validationIndicator.className = 'fa fa-question-circle';
            validationIndicator.title = 'No validation available for the selected provider! Maybe it works, maybe it does not work.';
        }
    }
};

MacroEditor.init = function (saveCallback) {
    let closure = this;
    [
        'macroEditor-submitClip',
        'macroEditor-submitEmote',
        'macroEditor-submitPause',
        'macroEditor-submitSound'
    ].forEach(id => {
        document.getElementById(id).addEventListener('click', (event) => {
            closure.saveAttrPressed(event);
        });
    });
    let closeButtons = this.editMacroModal.unwrap()[0].getElementsByClassName('macroEditor-closeButton');
    for (let i = 0; i < closeButtons.length; i++) {
        closeButtons[i].addEventListener('click', event => closure.dispose());
    }
    let modalSubmitForm = document.getElementById('macroEditor-footer');
    document.getElementById('modalEditMacroSubmit').addEventListener('click', event => {
        if (!modalSubmitForm.checkValidity()) {
            modalSubmitForm.classList.add('was-validated');
            return;
        }
        saveCallback(event);
        closure.dispose();
    });

    document.getElementById('macroEditor-emoteText').addEventListener('keyup', event => {
        closure.validateEmote(event);
    });
    document.getElementById('macroEditor-emoteProvider').addEventListener('change', event => {
        closure.validateEmote(event);
    });
};