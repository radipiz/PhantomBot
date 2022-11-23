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

/**
 * ttsSystem.js
 *
 * Plays Text2Speech on the PhantomBot Control Panel Alerts Page
 */
(function() {
    /**
     * @event command
     */
    $.bind('command', function(event) {
        var sender = event.getSender().toLowerCase(),
            command = event.getCommand(),
            args = event.getArgs(),
            subCommand = args[0],
            action = args[1],
            subAction = args[2],
            actionArgs = args[3],
            audioHook = args[1],
            audioHookListStr,
            isModv3 = $.checkUserPermission(sender, event.getTags(), $.PERMISSION.Mod);

        /* Synthesize text */
        if (command.equalsIgnoreCase('tts')) {
            var text = java.lang.String.join(' ', args);
            $.alertspollssocket.playTextToSpeech(text);
            return;
        }
    });

    /**
     * @event initReady
     */
    $.bind('initReady', function() {
        $.registerChatCommand('./systems/ttsSystem.js', 'tts', $.PERMISSION.Admin);
    });
})();