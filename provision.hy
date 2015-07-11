(import [sh [which brew git chmod ln]])
(import subprocess)
(import shutil)
(import toml)
(import os)


(defn log [msg]
  (print "Provision: " msg))

(defn installed? [cmd]
  (bool (shutil.which cmd)))

(defn run-cmd [cmd]
  (apply subprocess.call [cmd] {"shell" true}))

(defn run-cmd-squelch [cmd]
  "Run shell command and squelch output"
  (run-cmd (+ cmd " &> /dev/null")))

(defn check-cmd [cmd]
  "Check if a shell command returns true or false"
  (zero? (run-cmd-squelch cmd)))

(defn default-set? [domain key value]
  (check-cmd (.format "defaults read {} {} | grep '^{}$'" domain key value)))

(defn write-default [entry format-vars]
  (let [[domain (.get entry "domain" "")]
        [key (.get entry "key" "")]
        [type (.get entry "type" "")]
        [value (apply .format [(.get entry "value" "")] format-vars)]]
    (unless (default-set? domain key value)
      (run-cmd-squelch (.format "defaults write {} {} {} {}" domain key type value)))))

(defn installable [installed requested-packages]
  (remove (fn [pkg] (in pkg installed)) requested-packages))

(defn parse-installed [packages]
  (set (.split packages)))

(defn brew-install-packages [packages installed install-fn]
  (let [[packages-to-install (-> installed
                                 (parse-installed)
                                 (installable packages)
                                 (list))]]
    (when packages-to-install
      (apply install-fn packages-to-install))))

(defn brew-cask-install [packages]
  (brew-install-packages packages (brew "cask" "list") (brew.bake "cask" "install")))

(defn brew-install [packages]
  (brew-install-packages packages (brew "list") (brew.bake "install")))

(defn brew-tap [taps]
  (brew-install-packages taps (brew "tap") (brew.bake "tap")))


(defmain [&rest args]
  (log "Brew update")
  (brew "update")

  (with [[f (open "config.toml" "r")]]
        (let [[config (toml.load f)]
              [homebrew-packages (get config "homebrew" "packages")]
              [homebrew-casks (get config "homebrew" "casks")]
              [homebrew-taps (get config "homebrew" "taps")]
              [plist-files (get config "files" "plist")]
              [osx-config (get config "osx" "config")]
              [osx-scripts (get config "osx" "scripts")]]

          (log "Tap extra Homebrew repos")
          (brew-tap homebrew-taps)

          (log "Install Homebrew Packages")
          (brew-install homebrew-packages)

          (log "Install Homebrew Casks")
          (brew-cask-install homebrew-casks)

          (log "Configure OS X Settings")
          (setv format-vars (get config "osx" "variables"))
          (setv osx-config-list (list-comp
                                  v
                                  [[k v] (.items osx-config)]))
          (setv osx-script-list (list-comp
                                  v
                                  [[k v] (.items osx-scripts)]))

          (for [config osx-config-list]
               (if (instance? list config)
                 (for [config config]
                      (write-default config format-vars))
                 (write-default config format-vars)))

          (for [script osx-script-list]
               (if (instance? list script)
                 (for [script script]
                      (run-cmd-squelch (apply .format [(get script "cmd")] format-vars)))
                 (run-cmd-squelch (apply .format [(get script "cmd")] format-vars))))

          (log "Link plist configurations")
          (setv files-dir (os.path.join (os.getcwd) "files"))
          (setv plist-dir (os.path.expanduser "~/Library/Preferences"))
          (for [file plist-files]
               (let [[source (os.path.join files-dir file)]
                     [destination (os.path.join plist-dir file)]]
                 (ln "-Ffhs" source destination)
                 (chmod 600 destination)))))

  (log "Install/Update dotfiles")
  (setv dotfiles-dir (os.path.expanduser "~/.dotfiles"))
  (setv dotfiles-git "git@github.com:jmmk/.dotfiles")

  (unless (os.path.isdir dotfiles-dir)
    (git "clone" "-q" "--depth" 1 dotfiles-git dotfiles-dir))

  (os.chdir dotfiles-dir)
  (git "pull" "-q" "origin" "master")
  (log "run setup")
  (run-cmd "hy setup.hy")

  (setv zsh (which "zsh"))
  (unless (= (os.getenv "SHELL") zsh)
    (log "chsh to zsh")
    (run-cmd-squelch (.format "chsh -s {}" zsh))))
