package config;

import play.modules.guice.PlayAbstractModule;
import properties.PropertyPlaceholder;
import properties.PropertyPlaceholderImpl;
import security.Secure;
import security.SecureFap;
import security.SecureFapGen;
import services.FirmaService;
import services.GestorDocumentalService;
import services.RegistroService;
import services.filesystem.FileSystemFirmaServiceImpl;
import services.filesystem.FileSystemGestorDocumentalServiceImpl;
import services.filesystem.FileSystemRegistroService;

public class FapModule extends PlayAbstractModule {

	@Override
	protected void configure() {
		secure();
		propertyPlaceholder();
		gestorDocumental();
		firma();
		registro();
	}
	
	protected void gestorDocumental() {
		bindLazySingletonOnDev(GestorDocumentalService.class, FileSystemGestorDocumentalServiceImpl.class);
	}

	protected void firma() {
		bindLazySingletonOnDev(FirmaService.class, FileSystemFirmaServiceImpl.class);
	}
	   
	protected void registro(){
		bindLazySingletonOnDev(RegistroService.class, FileSystemRegistroService.class);
	}
	
	protected void secure() {
		bind(Secure.class).toInstance(new SecureFap(new SecureFapGen(null)));
	}

	protected void propertyPlaceholder() {
		bind(PropertyPlaceholder.class).toInstance(new PropertyPlaceholderImpl());
	}

}
