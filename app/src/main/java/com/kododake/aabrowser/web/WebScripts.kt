/*
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
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
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.kododake.aabrowser.web

object WebScripts {
    val DRM_L3_ENFORCER_JS = """
        (function() {
            try {
                const host = window.location.hostname;
                if (host && (
                    host === 'youtube.com' || host.endsWith('.youtube.com') ||
                    host === 'googlevideo.com' || host.endsWith('.googlevideo.com') ||
                    host === 'youtube-nocookie.com' || host.endsWith('.youtube-nocookie.com')
                )) {
                    return;
                }
            } catch (_) {}

            if (typeof navigator === 'undefined' || !navigator.requestMediaKeySystemAccess) {
                return;
            }

            const patchKey = typeof Symbol !== 'undefined' && Symbol.for ? Symbol.for('__aab_drm_l3_enforced__') : '__aab_drm_l3_enforced__';
            if (navigator[patchKey]) return;

            const originalRequest = navigator.requestMediaKeySystemAccess;
            const patchedRequest = function requestMediaKeySystemAccess(keySystem, configs) {
                if (keySystem === 'com.widevine.alpha' && configs) {
                    try {
                        const newConfigs = Array.from(configs).map(config => {
                            const newConfig = Object.assign({}, config);
                            if (config.videoCapabilities) {
                                newConfig.videoCapabilities = Array.from(config.videoCapabilities).map(cap => {
                                    const newCap = Object.assign({}, cap);
                                    if (newCap.robustness && typeof newCap.robustness === 'string' && newCap.robustness.startsWith('HW_SECURE')) {
                                        newCap.robustness = 'SW_SECURE_DECODE';
                                    }
                                    return newCap;
                                });
                            }
                            if (config.audioCapabilities) {
                                newConfig.audioCapabilities = Array.from(config.audioCapabilities).map(cap => {
                                    const newCap = Object.assign({}, cap);
                                    if (newCap.robustness && typeof newCap.robustness === 'string' && newCap.robustness.startsWith('HW_SECURE')) {
                                        newCap.robustness = 'SW_SECURE_DECODE';
                                    }
                                    return newCap;
                                });
                            }
                            return newConfig;
                        });
                        return originalRequest.call(navigator, keySystem, newConfigs).catch(function() {
                            return originalRequest.call(navigator, keySystem, configs);
                        });
                    } catch (_) {
                        return originalRequest.call(navigator, keySystem, configs);
                    }
                }
                return originalRequest.call(navigator, keySystem, configs);
            };

            try {
                Object.defineProperty(patchedRequest, 'name', { value: 'requestMediaKeySystemAccess' });
                patchedRequest.toString = function() {
                    return 'function requestMediaKeySystemAccess() { [native code] }';
                };
            } catch (_) {}

            try {
                Object.defineProperty(navigator, patchKey, { value: true, enumerable: false, configurable: false });
                Object.defineProperty(navigator, 'requestMediaKeySystemAccess', {
                    value: patchedRequest,
                    writable: true,
                    configurable: true
                });
            } catch (_) {
                navigator.requestMediaKeySystemAccess = patchedRequest;
            }
        })();
    """.trimIndent()

    private const val AUDIO_SYNC_DELAY_PLACEHOLDER = "__AAB_AUDIO_DELAY_MS__"

    /**
     * Delays the audio of <video> elements by routing it through a Web Audio DelayNode.
     *
     * Android Auto streams the screen to the head unit as an encoded video, which reaches the
     * car later than the audio. Delaying the audio by the same amount brings them back in sync.
     *
     * The script stays inert while the delay is 0. It skips media the page cannot route through
     * Web Audio (DRM-protected or cross-origin without CORS), because routing those would mute them.
     * The delay can be changed live through window.__aabAudioSync.set(ms).
     */
    private val AUDIO_SYNC_JS_TEMPLATE = """
        (function() {
            if (window.__aabAudioSync) {
                window.__aabAudioSync.set($AUDIO_SYNC_DELAY_PLACEHOLDER);
                return;
            }
            var MAX_DELAY_MS = 2000;
            var state = { delayMs: 0, ctx: null, nodes: [] };
            var routed = new WeakSet();
            var blocked = new WeakSet();

            function clamp(ms) {
                ms = Number(ms);
                if (!isFinite(ms) || ms < 0) return 0;
                return Math.min(ms, MAX_DELAY_MS);
            }

            function getContext() {
                if (state.ctx) return state.ctx;
                var Ctor = window.AudioContext || window.webkitAudioContext;
                if (!Ctor) return null;
                try { state.ctx = new Ctor(); } catch (_) { return null; }
                return state.ctx;
            }

            function isRoutable(el) {
                if (!el || el.tagName !== 'VIDEO') return false;
                if (routed.has(el) || blocked.has(el)) return false;
                if (el.mediaKeys) { blocked.add(el); return false; }
                var src = el.currentSrc || el.src;
                if (!src) return false;
                if (src.indexOf('blob:') === 0 || src.indexOf('data:') === 0) return true;
                try {
                    var url = new URL(src, location.href);
                    if (url.origin === location.origin) return true;
                } catch (_) { return false; }
                // Cross-origin media is only audible through Web Audio when loaded with CORS.
                return el.crossOrigin === 'anonymous' || el.crossOrigin === 'use-credentials';
            }

            function route(el) {
                if (state.delayMs <= 0 || !isRoutable(el)) return;
                var ctx = getContext();
                if (!ctx) return;
                try {
                    var source = ctx.createMediaElementSource(el);
                    var delay = ctx.createDelay(MAX_DELAY_MS / 1000);
                    delay.delayTime.value = state.delayMs / 1000;
                    source.connect(delay);
                    delay.connect(ctx.destination);
                    state.nodes.push(delay);
                    routed.add(el);
                } catch (_) {
                    blocked.add(el);
                }
                if (ctx.state === 'suspended') {
                    ctx.resume().catch(function() {});
                }
            }

            function scan() {
                var videos = document.getElementsByTagName('video');
                for (var i = 0; i < videos.length; i++) {
                    if (!videos[i].paused) route(videos[i]);
                }
            }

            try {
                var proto = window.HTMLMediaElement && HTMLMediaElement.prototype;
                if (proto && proto.setMediaKeys) {
                    var originalSetMediaKeys = proto.setMediaKeys;
                    proto.setMediaKeys = function(keys) {
                        if (keys) blocked.add(this);
                        return originalSetMediaKeys.apply(this, arguments);
                    };
                }
            } catch (_) {}

            document.addEventListener('encrypted', function(e) { blocked.add(e.target); }, true);
            document.addEventListener('playing', function(e) { route(e.target); }, true);
            document.addEventListener('play', function() {
                if (state.ctx && state.ctx.state === 'suspended') {
                    state.ctx.resume().catch(function() {});
                }
            }, true);

            window.__aabAudioSync = {
                set: function(ms) {
                    state.delayMs = clamp(ms);
                    var seconds = state.delayMs / 1000;
                    var ctx = state.ctx;
                    state.nodes.forEach(function(node) {
                        try {
                            node.delayTime.setTargetAtTime(seconds, ctx.currentTime, 0.05);
                        } catch (_) {
                            node.delayTime.value = seconds;
                        }
                    });
                    if (state.delayMs > 0) scan();
                },
                get: function() { return state.delayMs; }
            };
            window.__aabAudioSync.set($AUDIO_SYNC_DELAY_PLACEHOLDER);
        })();
    """.trimIndent()

    fun audioSyncJs(delayMs: Int): String =
        AUDIO_SYNC_JS_TEMPLATE.replace(AUDIO_SYNC_DELAY_PLACEHOLDER, delayMs.coerceAtLeast(0).toString())

    fun audioSyncSetJs(delayMs: Int): String =
        "window.__aabAudioSync && window.__aabAudioSync.set(${delayMs.coerceAtLeast(0)});"
}
