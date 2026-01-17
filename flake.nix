{
  inputs = {
    nixpkgs.url = "nixpkgs/nixos-25.11";
  };

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      java = pkgs.jdk25;
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          jdt-language-server
          java
          maven
        ];
      };

      apps.${system} = {
        run = {
          type = "app";
          program = "${pkgs.writeShellScript "run" ''
            ${pkgs.maven}/bin/mvn package
            exec ${java}/bin/java -cp target/bot.jar codes.blitz.game.Blitz2026Application
          ''}";
        };

        build = {
          type = "app";
          program = "${pkgs.writeShellScript "build" ''
            exec ${pkgs.maven}/bin/mvn package
          ''}";
        };
      };
    };
}
