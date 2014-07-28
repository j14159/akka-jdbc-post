# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|

end

Vagrant::Config.run do |config|
  config.vm.box = "precise64"
  config.vm.box_url = "http://files.vagrantup.com/precise64.box"
  config.vm.provision :shell, :path => "pg-setup.sh"
  config.vm.forward_port 5432, 15432
end