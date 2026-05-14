package com.neoruaa.xiaoaischedule.importer

object ImportJs {
    val BridgeGlue = """
        (function(){
          try {
            if(window._xiaoAiImportBridgeInjected) return;
            window._xiaoAiImportBridgeInjected = true;
            var reg = {};
            window._resolveAndroidPromise = function(id, r) {
              var p = reg[id];
              if (p) { delete reg[id]; p[0](r); }
            };
            window._rejectAndroidPromise = function(id, e) {
              var p = reg[id];
              if (p) { delete reg[id]; p[1](new Error(e)); }
            };
            function mkp(fn) {
              return new Promise(function(resolve, reject) {
                var id = '_bp' + Date.now() + Math.random().toString(36).slice(2);
                reg[id] = [resolve, reject];
                fn(id);
              });
            }
            function sarg(a) { return typeof a === 'string' ? a : JSON.stringify(a); }
            window.app = window.app || {};
            window.app.showAlert = function(t,c,b) {
              if (arguments.length === 1) return AndroidBridge.showAlert(sarg(t));
              if (arguments.length === 2) return AndroidBridge.showAlert(sarg(t), sarg(c));
              return AndroidBridge.showAlert(sarg(t), sarg(c), sarg(b || '确定'));
            };
            window.app.showPrompt = function(t,p,d,v) {
              if (arguments.length === 1) return AndroidBridge.showPrompt(sarg(t), '');
              if (arguments.length === 2) return AndroidBridge.showPrompt(sarg(t), sarg(p));
              if (arguments.length === 3) return AndroidBridge.showPrompt(sarg(t), sarg(p), sarg(d));
              return AndroidBridge.showPrompt(sarg(t), sarg(p), sarg(d || ''), sarg(v || ''));
            };
            window.app.showSingleSelection = function(t,i,d) {
              if (arguments.length === 1) return AndroidBridge.showSingleSelection(sarg(t), '[]');
              if (arguments.length === 2) return AndroidBridge.showSingleSelection(sarg(t), sarg(i));
              return AndroidBridge.showSingleSelection(sarg(t), sarg(i), d != null ? d : -1);
            };
            window.app.saveImportedCourses = function(j){ return AndroidBridge.saveImportedCourses(sarg(j)); };
            window.app.saveCourseConfig = function(j){ return AndroidBridge.saveCourseConfig(sarg(j)); };
            window.app.savePresetTimeSlots = function(j){ return AndroidBridge.savePresetTimeSlots(sarg(j)); };
            window.app.postData = function(m){ return AndroidBridge.postData(sarg(m)); };
            window.app.reportError = function(e){ return AndroidBridge.reportError(sarg(e)); };
            window.app.notifyTaskCompleted = function(){ AndroidBridge.notifyTaskCompletion(); };
            window.app.notifyTaskCompletion = function(){ AndroidBridge.notifyTaskCompletion(); };
            window.app.postHtml = function(h){ AndroidBridge.postHtml(sarg(h)); };
            window.app.closeWebView = function(){ AndroidBridge.closeWebView(); };
            window.app.close = window.app.closeWebView;
            window.AndroidBridgePromise = {
              showAlert:function(t,c,b){ return mkp(function(id){AndroidBridge.showAlertAsync(sarg(t),sarg(c),sarg(b || '确定'),id);}); },
              showPrompt:function(t,p,d,v){ return mkp(function(id){AndroidBridge.showPromptAsync(sarg(t),sarg(p),sarg(d || ''),sarg(v || ''),id);}); },
              showSingleSelection:function(t,i,d){ return mkp(function(id){AndroidBridge.showSingleSelectionAsync(sarg(t),sarg(i),d != null ? d : -1,id);}); },
              saveImportedCourses:function(j){ return mkp(function(id){AndroidBridge.saveImportedCourses(sarg(j),id);}); },
              saveCourseConfig:function(j){ return mkp(function(id){AndroidBridge.saveCourseConfig(sarg(j),id);}); },
              savePresetTimeSlots:function(j){ return mkp(function(id){AndroidBridge.savePresetTimeSlots(sarg(j),id);}); },
              notifyTaskCompleted:function(){ AndroidBridge.notifyTaskCompletion(); },
              notifyTaskCompletion:function(){ AndroidBridge.notifyTaskCompletion(); }
            };
          } catch(e) {
            console.error('xiaoai import bridge inject failed', e);
          }
        })();
    """.trimIndent()

    val SettingPatch = """
        (function(){
          function textOf(node){ return (node && node.textContent || '').trim(); }
          function hasClassPrefix(node, prefix) {
            return !!(node && typeof node.className === 'string' && node.className.split(/\s+/).some(function(c){ return c.indexOf(prefix) === 0; }));
          }
          function closestWrap(node) {
            var cur = node;
            for (var i = 0; i < 8 && cur; i += 1, cur = cur.parentElement) {
              if (hasClassPrefix(cur, 'wrap___')) return cur;
            }
            return node;
          }
          function bindOnce(node, key, handler) {
            if (!node || node[key]) return;
            node[key] = true;
            node.style.pointerEvents = 'auto';
            node.addEventListener('click', function(e){
              e.preventDefault();
              e.stopPropagation();
              handler();
              return false;
            }, true);
          }
          function replaceLeafText(from, to) {
            var walker = document.createTreeWalker(
              document.body,
              NodeFilter.SHOW_TEXT,
              {
                acceptNode: function(node) {
                  return node.nodeValue && node.nodeValue.indexOf(from) >= 0
                    ? NodeFilter.FILTER_ACCEPT
                    : NodeFilter.FILTER_REJECT;
                }
              }
            );
            var node;
            while ((node = walker.nextNode())) {
              node.nodeValue = node.nodeValue.replace(from, to);
            }
          }
          function patch(){
            try {
              if (!/#\/(set_schedule|setting)/.test(location.hash || '')) return;
              replaceLeafText('教务导入系统暂停维护中', '从教务系统中导入课表');
              replaceLeafText('选择学历', '关于模块');
              replaceLeafText('本科/专科', '小爱课程表复活计划');

              var importButton = document.getElementById('ai-class-shedule-fe-setting-button-jiaoyu');
              bindOnce(importButton, '__xiaoAiImportBound', function(){
                if (window.Android && Android.navSchoolScreen) Android.navSchoolScreen();
              });

              var labels = Array.prototype.slice.call(document.querySelectorAll('[class^="label___"], [class*=" label___"]'));
              labels.forEach(function(label){
                var text = textOf(label);
                if (text === '关于应用') {
                  var row = closestWrap(label);
                  if (row) {
                    row.setAttribute('aria-label', '关于 小爱课程表 Revived 应用');
                    bindOnce(row, '__xiaoAiAboutBound', function(){
                      if (window.Android && Android.navModuleScreen) Android.navModuleScreen();
                    });
                  }
                }
                if (text === '教务网站导入') {
                  var importRow = closestWrap(label);
                  if (importRow) {
                    importRow.setAttribute('aria-label', '教务网站导入 从教务系统中导入课表');
                  }
                }
              });
            } catch(e) {
              console.warn('xiaoai setting patch failed', e);
            }
          }
          patch();
          if (!window.__xiaoAiImportSettingPatchTimer) {
            window.__xiaoAiImportSettingPatchTimer = setInterval(patch, 800);
          }
          if (!window.__xiaoAiImportSettingPatchObserver && document.body) {
            window.__xiaoAiImportSettingPatchObserver = new MutationObserver(function(){ patch(); });
            window.__xiaoAiImportSettingPatchObserver.observe(document.body, {childList:true, subtree:true});
          }
        })();
    """.trimIndent()

    val ExtractHtml = """
        (function(){
          var html = document.documentElement ? document.documentElement.outerHTML : '';
          var frames = Array.prototype.slice.call(document.querySelectorAll('iframe,frame'));
          frames.forEach(function(frame){
            try {
              if (frame.contentDocument && frame.contentDocument.documentElement) {
                html += '\n<!-- frame -->\n' + frame.contentDocument.documentElement.outerHTML;
              }
            } catch(e) {}
          });
          AndroidBridge.postHtml(html);
        })();
    """.trimIndent()
}
