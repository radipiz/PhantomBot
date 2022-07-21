/*
 * Copyright (C) 2016-2021 phantombot.github.io/PhantomBot
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

page = {
    // Constants
    // Meta information for the database and to connect it to HTML
    moduleId: 'overlay',
    databaseModuleId: 'overlay_module',
    moduleConfigTable: 'overlay',
    formId: 'alertsOverlayModule',
    outputElementId: 'overlayUrl',

    // Config entries
    enableAudioHooks: 'enableAudioHooks',
    audioHookVolume: 'audioHookVolume',
    enableFlyingEmotes: 'enableFlyingEmotes',
    enableGifAlerts: 'enableGifAlerts',
    gifAlertVolume: 'gifAlertVolume',
    enableVideoClips: 'enableVideoClips',
    videoClipVolume: 'videoClipVolume',
    enableDebug: 'enableDebug',

    // members
    inputElements: null,
    outputElement: null,
};
page.init = function () {
    // Keep a reference of this for anonymous functions
    let closure = this;

    // This module cannot be enabled or disabled since it provides a resource and just helps to configure
    // this resource file. The stored values are just the last used settings.
    socket.getDBValues(this.databaseModuleId, {
        tables: [
            this.moduleConfigTable, this.moduleConfigTable, this.moduleConfigTable,
            this.moduleConfigTable, this.moduleConfigTable, this.moduleConfigTable,
            this.moduleConfigTable, this.moduleConfigTable,
        ],
        keys: [
            this.enableAudioHooks, this.audioHookVolume, this.enableFlyingEmotes,
            this.enableGifAlerts, this.gifAlertVolume, this.enableDebug,
            this.enableVideoClips, this.videoClipVolume
        ]
    }, true, function (config) {
        // handle boolean values
        [
            closure.enableAudioHooks,
            closure.enableFlyingEmotes,
            closure.enableGifAlerts,
            closure.enableVideoClips,
            closure.enableDebug
        ].forEach((key) => {
            let inputNode = document.getElementById(closure.moduleId + key[0].toUpperCase() + key.slice(1));
            // only accept 'true' and 'false' as valid
            if (config[key] === 'true' || config[key] === 'false') {
                inputNode.checked = config[key] === 'true';
            } else {
                // set the default value otherwise
                inputNode.checked = inputNode.defaultChecked;
            }
        });
        // handle decimals
        [closure.audioHookVolume, closure.gifAlertVolume, closure.videoClipVolume].forEach((key) => {
            let inputNode = document.getElementById(closure.moduleId + key[0].toUpperCase() + key.slice(1));
            // Convert the value to a String and then to number to validate it
            // and use the default value, if it's invalid
            let value = Number(String(config[key]));
            inputNode.value = isNaN(value) ? inputNode.placeholder : value;
        });
    });

    // Connect DOM to Code

    this.outputElement = document.forms[this.formId][this.outputElementId];
    // get a list of all elements that should get an event listener on their change to trigger an update of the url
    this.inputElements = document.forms[this.formId].getElementsByClassName('trigger-update');

    Array.prototype.forEach.call(closure.inputElements, (element) => {
        element.addEventListener("change", (event) => {
            closure.buildOverlayUrl(event);
            window.helpers.debounce(function(){ closure.save(); }, 1000)();
        });
    });

    // finally generate the url once
    this.buildOverlayUrl();
};

page.buildOverlayUrl = function () {
    this.outputElement.value = `${window.location.protocol}//${window.location.host}/alerts?`;
    let stringSettings = Array.prototype.filter.call(this.inputElements, (element) => element.type !== 'checkbox')
        .map((element) => {
            let name = element.id.slice(this.moduleId.length);
            return `${name[0].toLowerCase()}${name.slice(1)}=${element.value}`
        })
        .join('&');
    let booleanSettings = Array.prototype.filter.call(this.inputElements, (element) => element.type === 'checkbox')
        .map((element) => {
            let name = element.id.slice(this.moduleId.length);
            return `${name[0].toLowerCase()}${name.slice(1)}=${element.checked}`
        })
        .join('&');
    this.outputElement.value += stringSettings + '&' + booleanSettings;
};

page.save = function() {
    // Collect values from the form with special caution to checkbox elements
    let keysStrings = [ this.audioHookVolume, this.gifAlertVolume, this.videoClipVolume ];
    let keysCheckboxes = [ this.enableAudioHooks, this.enableFlyingEmotes, this.enableGifAlerts, this.enableVideoClips, this.enableDebug ];
    let valuesStrings = keysStrings.map(key => this.inputElements[this.moduleId + key[0].toUpperCase() + key.slice(1)].value);
    let valuesCheckboxes = keysCheckboxes.map(key => this.inputElements[this.moduleId + key[0].toUpperCase() + key.slice(1)].checked);
    let keys = keysStrings.concat(keysCheckboxes);
    let values = valuesStrings.concat(valuesCheckboxes);
    // Save the values in the database omitting the success callback because value changes can happen often and fast
    socket.updateDBValues(this.databaseModuleId, {
        tables: [
            this.moduleConfigTable, this.moduleConfigTable, this.moduleConfigTable,
            this.moduleConfigTable, this.moduleConfigTable, this.moduleConfigTable,
            this.moduleConfigTable, this.moduleConfigTable
        ],
        keys: keys,
        values: values
    });
};

page.main = function () {
    this.init();
};

// Run the page
$(function() { page.main(); });