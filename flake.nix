{
  description =
    "Jellyfin Sidekick -- Write NFO tags to media files and trigger Jellyfin refresh";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-25.05";
    utils.url = "github:numtide/flake-utils";
    nix-helpers = {
      url = "github:fudoniten/fudo-nix-helpers";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, utils, nix-helpers, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages."${system}";
        helpers = nix-helpers.legacyPackages."${system}";
        cljLibs = { };
      in {
        packages = rec {
          default = jellyfinSidekick;

          jellyfinSidekick = helpers.mkClojureBin {
            name = "org.fudo/jellyfin-sidekick";
            primaryNamespace = "jellyfin-sidekick.main";
            src = ./.;
          };

          deployContainer = helpers.deployContainers {
            name = "jellyfin-sidekick";
            repo = "registry.kube.sea.fudo.link";
            tags = [ "latest" ];
            entrypoint =
              let jellyfinSidekick = self.packages."${system}".jellyfinSidekick;
              in [ "${jellyfinSidekick}/bin/jellyfin-sidekick" ];
            verbose = true;
          };
        };

        checks = {
          clojureTests = pkgs.runCommand "clojure-tests" { } ''
            mkdir -p $TMPDIR
            cd $TMPDIR
            ${pkgs.clojure}/bin/clojure -M:test
          '';
        };

        devShells = rec {
          default = updateDeps;
          updateDeps = pkgs.mkShell {
            buildInputs = [ (helpers.updateClojureDeps cljLibs) ];
          };
          jellyfinSidekickServer = pkgs.mkShell {
            buildInputs = with self.packages."${system}"; [ jellyfinSidekick ];
          };
        };

        apps = rec {
          default = deployContainer;
          deployContainer = {
            type = "app";
            program =
              let deployContainer = self.packages."${system}".deployContainer;
              in "${deployContainer}/bin/deployContainers";
          };
        };
      });
}
