(ns provision.core
  (:require [planck.core :as planck]
            ; [cljs.reader :as edn] Broken for now
            ; [clojure.string :as string]
            [clojure.set]
            [planck.shell :as cli]))


(defn log [& msg]
  (apply println "Provision: " msg))

(defn run-cmd-verbose [& cmd]
  (println (:out (apply cli/sh cmd))))

(defn check-cmd-status [& cmd]
  (zero? (:exit (apply cli/sh cmd))))

(defn get-cmd-output [& cmd]
  (:out (apply cli/sh cmd)))

(defn installed? [cmd]
  (check-cmd "which" cmd))

; (defn default-set? [domain key value]
;   (-> (run-cmd "defaults" "read" domain key)
;       (check-cmd "grep" (str "'^" value "$'"))))

; (defn write-default [entry format-vars]
;   (let [[domain (.get entry "domain" "")]
;         [key (.get entry "key" "")]
;         [type (.get entry "type" "")]
;         [value (apply .format [(.get entry "value" "")] format-vars)]]
;     (unless (default-set? domain key value)
;       (run-cmd-squelch (.format "defaults write {} {} {} {}" domain key type value)))))

(defn installable [installed requested-packages]
  (clojure.set/difference requested-packages installed))

(defn parse-installed [packages]
  (set (clojure.string/split packages "\n")))

(defn brew-install-packages [packages installed install-fn]
  (let [packages-to-install (-> installed
                                 (parse-installed)
                                 (installable packages))]
    (log "Installing " (or packages-to-install #{}))
    (when packages-to-install
      (apply install-fn packages-to-install))))

(defn brew-cask-install [packages]
  (brew-install-packages packages
                         (get-cmd-output "brew" "cask" "list")
                         (partial cli/sh "brew" "cask" "install")))

(defn brew-install [packages]
  (brew-install-packages packages
                         (get-cmd-output "brew" "list")
                         (partial cli/sh "brew" "install")))

(defn brew-tap [taps]
  (brew-install-packages taps
                         (get-cmd-output "brew" "tap")
                         (partial cli/sh "brew" "tap")))


(defn -main [& args]
  (let [config (cljs.reader/read-string (planck/slurp "config.edn"))
        homebrew-packages (get-in config [:homebrew :packages])
        homebrewk-casks (get-in config [:homebrew :casks])
        homebrew-taps (get-in config [:homebrew :taps])
        plist-files (get-in config [:files :plist])
        osx-config (get-in config [:osx :config])
        osx-scripts (get-in config [:osx :scripts])]

    (log "Brew update")
    (run-cmd-verbose "brew" "update")

    (log "Tap extra Homebrew repos")
    (brew-tap homebrew-taps)

    (log "Install Homebrew Packages")
    (brew-install homebrew-packages)

    (log "Install Homebrew Casks")
    (brew-cask-install homebrew-casks)))

    ;           (log "Configure OS X Settings")
    ;           (setv format-vars (get config "osx" "variables"))
    ;           (setv osx-config-list (list-comp
    ;                                   v
    ;                                   [[k v] (.items osx-config)]))
    ;           (setv osx-script-list (list-comp
    ;                                   v
    ;                                   [[k v] (.items osx-scripts)]))

    ;           (for [config osx-config-list]
    ;                (if (instance? list config)
    ;                  (for [config config]
    ;                       (write-default config format-vars))
    ;                  (write-default config format-vars)))

    ;           (for [script osx-script-list]
    ;                (if (instance? list script)
    ;                  (for [script script]
    ;                       (run-cmd-squelch (apply .format [(get script "cmd")] format-vars)))
    ;                  (run-cmd-squelch (apply .format [(get script "cmd")] format-vars))))

    ;           (log "Link plist configurations")
    ;           (setv files-dir (os.path.join (os.getcwd) "files"))
    ;           (setv plist-dir (os.path.expanduser "~/Library/Preferences"))
    ;           (for [file plist-files]
    ;                (let [[source (os.path.join files-dir file)]
    ;                      [destination (os.path.join plist-dir file)]]
    ;                  (ln "-Ffhs" source destination)
    ;                  (chmod 600 destination)))))

    ;   (log "Install/Update dotfiles")
    ;   (setv dotfiles-dir (os.path.expanduser "~/.dotfiles"))
    ;   (setv dotfiles-git "git@github.com:jmmk/.dotfiles")

    ;   (unless (os.path.isdir dotfiles-dir)
    ;     (git "clone" "-q" "--depth" 1 dotfiles-git dotfiles-dir))

    ;   (os.chdir dotfiles-dir)
    ;   (git "pull" "-q" "origin" "master")
    ;   (log "run setup")
    ;   (run-cmd "hy setup.hy")

    ;   (setv zsh (which "zsh"))
    ;   (unless (= (os.getenv "SHELL") zsh)
    ;     (log "chsh to zsh")
    ;     (run-cmd-squelch (.format "chsh -s {}" zsh))))
