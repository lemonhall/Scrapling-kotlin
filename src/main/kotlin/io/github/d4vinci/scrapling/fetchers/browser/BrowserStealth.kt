package io.github.d4vinci.scrapling.fetchers.browser

internal object BrowserStealth {
    fun initScript(options: BrowserFetchOptions, stealth: Boolean): String? {
        if (!stealth && !options.blockWebRtc && options.allowWebgl && !options.hideCanvas) {
            return null
        }

        val blockWebRtcValue = if (options.blockWebRtc) "true" else "false"
        val allowWebglValue = if (options.allowWebgl) "true" else "false"
        val hideCanvasValue = if (options.hideCanvas) "true" else "false"
        val stealthValue = if (stealth) "true" else "false"
        return """
            (() => {
              const blockWebRtc = $blockWebRtcValue;
              const allowWebgl = $allowWebglValue;
              const hideCanvas = $hideCanvasValue;
              const stealthMode = $stealthValue;
              if (stealthMode) {
                const navigatorPrototype = Object.getPrototypeOf(window.navigator);
                const target = navigatorPrototype ?? window.navigator;
                Object.defineProperty(target, 'webdriver', {
                  get: () => undefined,
                  configurable: true
                });
                if (!window.chrome) {
                  Object.defineProperty(window, 'chrome', {
                    value: { runtime: {} },
                    configurable: true
                  });
                } else if (!window.chrome.runtime) {
                  Object.defineProperty(window.chrome, 'runtime', {
                    value: {},
                    configurable: true
                  });
                }
                const originalQuery = window.navigator.permissions?.query?.bind(window.navigator.permissions);
                if (originalQuery) {
                  window.navigator.permissions.query = (parameters) => {
                    if (parameters && parameters.name === 'notifications') {
                      return Promise.resolve({ state: Notification.permission });
                    }
                    return originalQuery(parameters);
                  };
                }
              }
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
              if (hideCanvas && window.CanvasRenderingContext2D) {
                const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function(...args) {
                  const imageData = originalGetImageData.apply(this, args);
                  if (imageData && imageData.data && imageData.data.length >= 4) {
                    imageData.data[0] = (imageData.data[0] + 1) % 256;
                  }
                  return imageData;
                };
              }
            })();
        """.trimIndent()
    }
}
