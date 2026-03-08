package io.github.d4vinci.scrapling.fetchers.browser

internal object BrowserStealth {
    fun initScript(options: BrowserFetchOptions): String? {
        if (!options.blockWebRtc && options.allowWebgl) {
            return null
        }

        val blockWebRtcValue = if (options.blockWebRtc) "true" else "false"
        val allowWebglValue = if (options.allowWebgl) "true" else "false"
        return """
            (() => {
              const blockWebRtc = $blockWebRtcValue;
              const allowWebgl = $allowWebglValue;
              if (blockWebRtc) {
                Object.defineProperty(window, 'RTCPeerConnection', { value: undefined, configurable: true });
                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                  navigator.mediaDevices.getUserMedia = () => Promise.reject(new Error('WebRTC blocked by scrapling-kotlin'));
                }
              }
              if (!allowWebgl) {
                const originalGetContext = HTMLCanvasElement.prototype.getContext;
                HTMLCanvasElement.prototype.getContext = function(type, attrs) {
                  if (typeof type === 'string' && type.toLowerCase().includes('webgl')) {
                    return null;
                  }
                  return originalGetContext.call(this, type, attrs);
                };
              }
            })();
        """.trimIndent()
    }
}
