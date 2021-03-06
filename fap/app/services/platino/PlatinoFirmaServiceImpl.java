package services.platino;

import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.soap.SOAPFaultException;

import messages.Messages;
import models.Documento;
import models.Firmante;
import models.RepresentantePersonaFisica;
import models.RepresentantePersonaJuridica;
import models.Solicitante;
import net.java.dev.jaxb.array.StringArray;

import org.apache.cxf.binding.soap.SoapFault;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


import platino.Firma;
import platino.FirmanteCertificado;
import platino.InfoCert;
import platino.KeystoreCallbackHandler;
import platino.PlatinoCXFSecurityHeaders;
import platino.PlatinoProxy;
import play.libs.Codec;
import properties.PropertyPlaceholder;
import services.FirmaServiceException;
import services.GestorDocumentalService;
import services.GestorDocumentalServiceException;
import sun.security.pkcs.PKCS7;
import utils.BinaryResponse;
import utils.WSUtils;
import es.gobcan.eadmon.aed.ws.AedExcepcion;
import es.gobcan.platino.servicios.sfst.FirmaService;
import es.gobcan.platino.servicios.sfst.PlatinoSignatureServerBean;
import es.gobcan.platino.servicios.sfst.SignatureServiceException_Exception;
import es.gobcan.platino.servicios.sfst.ValidateCertResult;

/**
 * FirmaServiceImpl
 */
public class PlatinoFirmaServiceImpl implements services.FirmaService {

	private static Logger log = Logger.getLogger(PlatinoFirmaServiceImpl.class);
	
	private PlatinoSignatureServerBean firmaPort;
	private PropertyPlaceholder propertyPlaceholder;
	
	/*
	private static final int CERT_OK = 6;
	private static final int CERT_NO_VALIDO = 2;
	private static final int CERT_NO_CONFIANZA = 3;
	private static final int CERT_REVOCADO = 4;
	private static final int CERT_NO_VERIFICADO = 5;
	private static final int CADENA_CERT_NO_VALIDA = 25;
	*/
	
	private final String INVOKING_APP;
	private final String ALIAS;
	private final String JS_ENTORNO;
	
	@Inject
	public PlatinoFirmaServiceImpl(PropertyPlaceholder propertyPlaceholder){
		this.propertyPlaceholder = propertyPlaceholder;
		
        URL wsdlURL = PlatinoFirmaServiceImpl.class.getClassLoader().getResource("wsdl/firma-pre.wsdl");
        firmaPort = new FirmaService(wsdlURL).getFirmaService();
        WSUtils.configureEndPoint(firmaPort, getEndPoint());
        WSUtils.configureSecurityHeaders(firmaPort, propertyPlaceholder);
        PlatinoProxy.setProxy(firmaPort, propertyPlaceholder);
        
        //Properties
        INVOKING_APP = propertyPlaceholder.get("fap.platino.firma.invokingApp");
        ALIAS = propertyPlaceholder.get("fap.platino.firma.alias");
        JS_ENTORNO = properties.FapProperties.get("fap.platino.firma.js");
	}
	
	public boolean isConfigured(){
	    return hasConnection();
	}
	
	private boolean hasConnection() {
		boolean hasConnection = false;
		try {
			hasConnection = getVersion() != null;
			log.info("El servicio tiene conexion con " + getEndPoint() + "? :"+hasConnection);
		}catch(Exception e){
			log.info("El servicio no tiene conexion con " + getEndPoint());
		}
		return hasConnection; 
	}

	private String getVersion() throws FirmaServiceException {
	    try {
	        String version = firmaPort.getVersion();
	        return version;
	    }catch(Exception e){
	        throw newFirmaServiceException("Error al hacer getVersion", e);
	    }
	}
	
	private String getEndPoint() {
		return propertyPlaceholder.get("fap.platino.firma.url");
	}

	private FirmaServiceException newFirmaServiceException(String msg, Exception cause){
	    if(cause instanceof SignatureServiceException_Exception){
	        SignatureServiceException_Exception signatureException = (SignatureServiceException_Exception) cause;
	        return new FirmaServiceException(msg + " - " + signatureException.getFaultInfo().getMessage(), cause);
	    }else{
	        return new FirmaServiceException("Error al realizar firma pkcs7", cause);
	    }
	}
	
    public List<String> getFirmaEnClienteJS() {
        List<String> jsclient = new ArrayList<String>();
        String host;
        if("pre".equals(JS_ENTORNO.toLowerCase())){
            host = "http://www-pre.gobiernodecanarias.org/platino/servicios/sfst/js/";
        }else{
            host = "http://www.gobiernodecanarias.org/platino/servicios/sfst/js/";
        }
        jsclient.add(host + "WS_Full.js");
        jsclient.add(host + "sfest.utils.js");
        jsclient.add(host + "sfest.base.js");
        jsclient.add("/public/javascripts/firma/firma.js");
        jsclient.add("/public/javascripts/firma/firma-platino.js");
        jsclient.add("/public/javascripts/msg.js");
        return jsclient;
    }
	
    public String firmarTexto(byte[] texto) throws FirmaServiceException {
        String firma = null;
        try {
            firma = firmaPort.signPKCS7(texto, INVOKING_APP, ALIAS);
        }catch (Exception e) {
            throw newFirmaServiceException("Error al realizar firma pkcs7", e);
        }
        return firma;
    }
	
    public boolean validarFirmaTexto(byte[] texto, String firma) throws FirmaServiceException{
        boolean result = false;
        try {
            result = firmaPort.verifyPKCS7Signature(texto, firma.getBytes(), INVOKING_APP);
        } catch (Exception e) {
            throw newFirmaServiceException("Erro al validar la firma pkcs7", e);
        }
        return result;  
    }

    public String firmarDocumento(byte[] contenidoDocumento) throws FirmaServiceException {
        String firma = null;
        try {
            firma = firmaPort.signContent(contenidoDocumento, INVOKING_APP, ALIAS);
        } catch (Exception e) {
            throw newFirmaServiceException("Error al firmar XMLSignature ", e);
        }
        return firma;
    }
    
    public boolean validarFirmaDocumento(byte[] contenidoDocumento, String firma) throws FirmaServiceException {
        boolean result = false;
        try {
            result = firmaPort.verifyContentSignature(contenidoDocumento, firma.getBytes(), INVOKING_APP);
        } catch (SignatureServiceException_Exception e) {
            log.error("Error verificando el contenido de la firma", e);
        }
        return result;        
    }

    public InfoCert extraerCertificado(String firma) throws FirmaServiceException {
        String certificado = extraerCertificadoDeFirma(firma);
        boolean certificadoValido = isValidCertificado(certificado);
        if(!certificadoValido)
            throw new FirmaServiceException("El certificado no es válido");
        return getInformacion(certificado);
    }

	private String extraerCertificadoDeFirma(String firma) throws FirmaServiceException {
		String certificado = null;
		try {
			PKCS7 pkcs7 = new PKCS7(Codec.decodeBASE64(firma));
			X509Certificate certificate = pkcs7.getCertificates()[0];
			byte[] certificadoEncoded = certificate.getEncoded();
			certificado = Codec.encodeBASE64(certificadoEncoded);
		} catch (Exception e) {
			throw new FirmaServiceException("Error al extraer el certificado de la firma", e);
		}
		return certificado;
	}
	
	private boolean isValidCertificado(String certificado) throws FirmaServiceException{
		try {
			ValidateCertResult result = firmaPort.validateCert(certificado, INVOKING_APP);
			return result.getCode() == 6; //Codigo 6 Certificado OK
		} catch (Exception e) {
		    throw new FirmaServiceException("Error validando el certificado", e);
		}
	}
		
	private InfoCert getInformacion(String certificado) throws FirmaServiceException{
		try {
			List<StringArray> certInfo = firmaPort.getCertInfo(certificado, INVOKING_APP);
			InfoCert infoCert = new InfoCert(certInfo);
			return infoCert;
		} catch (Exception e) {
			throw newFirmaServiceException("Error al extraer la información del certificado", e);
		}
	}
	

	/*
	@Override
	public HashMap<String,String> extraerInfoFromFirma(String firma) {
		try {
			return extraerInformacionPersonal(extraerCertificado(firma));
		} catch (ParserConfigurationException e) {
			log.error("Error al parsear al extraer el certificado "+e);
		} catch (SAXException e) {
			log.error("Error al parsear al extraer el certificado. REINSTALAR EL APPLET O ACTIVEX "+e);
		} catch (IOException e) {
			log.error("Error en extraerInfoFromFirma "+e);
		}
		return null;
	}
	
	private HashMap<String,String> extraerInformacionPersonal(String certificado) {
		try {
			List<StringArray> certificadoInfo = getCertInfo(certificado);
			HashMap<String,String>values = new HashMap<String, String>();
			if (certificadoInfo != null) {
				for (StringArray array : certificadoInfo) {
					String key = array.getItem().get(0);
					String value = array.getItem().get(1);
					if (key != null) {
						values.put(key.toUpperCase(), value);
					}
				}
			}
			return values;
		} catch (Exception e) {
			return null;
		}
	}
	

	@Override
	public List<StringArray> getCertInfo(String certificado) throws Exception {
		try {
			String invokingApp = propertyPlaceholder.get("fap.platino.firma.invokingApp");
			return getFirmaPort().getCertInfo(certificado, invokingApp);
		} catch (Exception e) {
			log.error("Error al recuperar la información del certificado"+e);
		}
		return null;
	}
	

	@Override
	public Firmante validateXMLSignature(byte[] contenidoDoc, String firma) {
		try {

			String certificado = extraerCertificado(firma);
			ValidateCertResult result = validarCertificadoVR(certificado);
			
			if (result.getCode() == CERT_OK) {
				//Valida la firma
				if (verificarContentSignature(contenidoDoc, firma.getBytes())) {
					
					//Firma válida, extrae la informacion del certificado
					HashMap<String,String> certData = extraerInfoFromFirma(firma);
					Firmante firmante = null;
					
					
					//El certificado es de un NIF o NIE
					if (certData != null && certData.containsKey("NIF")) {
						play.Logger.debug("El certificado es un NIF o un CIE");
						
						firmante = new Firmante();
						firmante.idtipo = "nif";
						firmante.idvalor = certData.get("NIF");
						
						if (certData.containsKey("NOMBRECOMPLETO")){ 
							firmante.nombre = certData.get("NOMBRECOMPLETO");
						}else if (certData.containsKey("APELLIDOS")){ 
							firmante.nombre = certData.get("NOMBRE") + " " + certData.get("APELLIDOS");
						}else if (certData.containsKey("APELLIDO1")) {
							String nombre = certData.get("NOMBRE") + " " + certData.get("APELLIDO1");
							if (certData.containsKey("APELLIDO2"))
								nombre = nombre + " " + certData.get("APELLIDO2"); 
							firmante.nombre = nombre;
						}
					}
					else if (certData != null && certData.containsKey("CIF")) {
						play.Logger.debug("El certificado es un CIF");
						
						firmante = new Firmante();
						firmante.idtipo = "cif";
						firmante.idvalor = certData.get("CIF");
						
						if (certData.containsKey("NOMBRECOMPLETO")){ 
							firmante.nombre = certData.get("NOMBRECOMPLETO");
						}else if (certData.containsKey("APELLIDOS")){ 
							firmante.nombre = certData.get("NOMBRE") + " " + certData.get("APELLIDOS");
						}else if (certData.containsKey("APELLIDO1")) {
							String nombre = certData.get("NOMBRE") + " " + certData.get("APELLIDO1");
							if (certData.containsKey("APELLIDO2"))
								nombre = nombre + " " + certData.get("APELLIDO2"); 
							firmante.nombre = nombre;
						}
					}
					return firmante;
				}
				return null;
			} else {
				switch (result.getCode()) {
					case CERT_NO_VALIDO: log.error("certificadoNoValido"); break;
					case CERT_NO_CONFIANZA: log.error("certificadoNoConfianza"); break;
					case CERT_NO_VERIFICADO: log.error("certificadoNoVerificado"); break;
					case CERT_REVOCADO: log.error("certificadoRevocado"); break;
					case CADENA_CERT_NO_VALIDA: log.error("cadenaNoValida"); break;
				}
			}
		} catch (ParserConfigurationException e) {
			log.error("Error al parsear al extraer el certificado "+e);
		} catch (SAXException e) {
			log.error("Error al parsear al extraer el certificado. REINSTALAR EL APPLET O ACTIVEX "+e);
		} catch (IOException e) {
			log.error("Error al parsear al extraer el certificado "+e);
		}catch (Exception e) {
			log.error("Error en validateXMLSignature "+e);
			Messages.error("Error al validar la firma");
		}
		return null;
	}*/
	
	/*
	private String extraerCertificado(String firma) throws ParserConfigurationException, SAXException, IOException {
		//Extrayendo el certificado de la firma 
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db  = dbf.newDocumentBuilder();
        org.w3c.dom.Document doc = db.parse(new InputSource(new StringReader(firma)));
        //Pillamos certificado
        Element x509Certificate = (Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "X509Certificate").item(0);
        return x509Certificate.getTextContent();
	}
	
	private boolean isFirmanteCertificate(String tipoDoc, String docId, FirmanteCertificado firmante) {
		//Por alguna razon los certificados no distingeun entre NIE Y NIF y se ponen lso dos en el mismo campo como NIF
		String newTipoDoc = tipoDoc;
		if (tipoDoc.equalsIgnoreCase("NIE"))
			newTipoDoc = "NIF";
		//Si son el mismo tipo de documento el del ususario y el del certificado
		if (firmante != null && firmante.getTipoDocumento().equalsIgnoreCase(newTipoDoc)) 
			return docId.equals(firmante.getDocumento());
		return false;
	}
	
	@Override
	public void firmar(Documento documento, List<Firmante> firmantes, Firma firma){	
		firmar(documento, firmantes, firma, null);
	}
	
	private Firmante getFirmante(String firma, Documento documento){
		if(firma == null || firma.isEmpty()){
			Messages.error("La firma llegó vacía");
			return null;
		}	
		Firmante firmante = null;
		try {
		    BinaryResponse response = aedService.getDocumento(documento);
			byte[] contenido = response.getBytes();
			firmante = validateXMLSignature(contenido, firma);
			if(firmante == null){
				Messages.error("Error validando la firma");
			}
		} catch (Exception e) {
			play.Logger.error("Error obteniendo el documento del AED para verificar la firma. Uri = " + documento.uri);
			Messages.error("Error validando la firma");
		}
		return firmante;
	}
	

	@Override
	public void firmar(Documento documento, List<Firmante> firmantes, Firma firma, String valorDocumentofirmanteSolicitado){		
		Firmante firmanteCertificado = getFirmante(firma.firma, documento);
		
		if(firmanteCertificado != null){
			log.info("Firmante validado");
			
			int index = firmantes.indexOf(firmanteCertificado);
			Firmante firmante = null;
			if(index == -1){
				Messages.error("El certificado no se corresponde con uno que debe firmar la solicitud.");
			}else{
				firmante = firmantes.get(index);
				if(firmante.fechaFirma != null){
					Messages.error("Ya ha firmado la solicitud");
				}
				
				log.info("Firmante encontrado " + firmante.idvalor );
				log.info("Esperado " + valorDocumentofirmanteSolicitado);
				if(valorDocumentofirmanteSolicitado != null && !firmante.idvalor.equalsIgnoreCase(valorDocumentofirmanteSolicitado)){
					Messages.error("Se esperaba la firma de " + valorDocumentofirmanteSolicitado);
				}
			}
			
			if(!Messages.hasErrors()){
				// Guarda la firma en el AED
				try {
					log.info("Guardando firma en el aed");
					firmante.fechaFirma = new DateTime();
					aedService.agregarFirma(documento, new models.Firma(firma.firma, firmantes));
					firmante.save();
					
					log.info("Firma del documento " + documento.uri + " guardada en el AED");
				}catch(GestorDocumentalServiceException e){
					log.error("Error guardando la firma en el aed");
					Messages.error("Error al guardar la firma");
				}				
			}
		}else{
			log.error("firmanteCertificado == null????");
		}
	}

	@Override
	public void firmarFH(Documento documento, Firma firma){		
		Firmante firmante = getFirmante(firma.firma, documento);
		
		if((firmante != null)&&(firmante.esFuncionarioHabilitado())){
			log.info("Funcionario habilitado validado");
			log.info("Firmante encontrado " + firmante.idvalor );
			
			if(!Messages.hasErrors()){
				// Guarda la firma en el AED
				try {
					log.info("Guardando firma en el aed");
					firmante.fechaFirma = new DateTime();
					aedService.agregarFirma(documento, new models.Firma(firma.firma, firmante));
					firmante.save();
					
					log.info("Firma del documento " + documento.uri + " guardada en el AED");
				}catch(GestorDocumentalServiceException e){
					log.error("Error guardando la firma en el aed");
					Messages.error("Error al guardar la firma");
				}				
			}
		}else{
			Messages.error("El certificado no se corresponde con uno que debe firmar la solicitud.");
		}
	}

	public boolean hanFirmadoTodos(List<Firmante> firmantes){
		boolean multiple = true;
		for(Firmante f : firmantes){
			//Firmante único que ya ha firmado
			if(f.cardinalidad.equals("unico") && f.fechaFirma != null)
				return true;
			
			//Uno de los firmantes multiples no ha firmado
			if(f.cardinalidad.equals("multiple") && f.fechaFirma == null)
				multiple = false;
		}
		
		//En el caso de que no haya firmado ningún único
		//Se devuelve true si todos los múltiples han firmado
		return multiple;
	}
	
	 * Borra una lista de firmantes, borrando cada uno de los firmantes y vaciando la lista
	 * @param firmantes
	public void borrarFirmantes(List<Firmante> firmantes){
		List<Firmante> firmantesBack = new ArrayList<Firmante>(firmantes);
		firmantes.clear();
		
		for(Firmante f : firmantesBack)
			f.delete();
	}
	
	public void calcularFirmantes(Solicitante solicitante, List<Firmante> firmantes){
		if(solicitante == null) throw new NullPointerException();
		if(firmantes == null) throw new NullPointerException();
		
		//Solicitante de la solicitud
		Firmante firmanteSolicitante = new Firmante(solicitante, "unico");
		firmantes.add(firmanteSolicitante);
		
		//Comprueba los representantes
		if(solicitante.isPersonaFisica() && solicitante.representado){
			// Representante de persona física
			Firmante representante = new Firmante(solicitante.representante, "representante", "unico");
			firmantes.add(representante);
		}else if(solicitante.isPersonaJuridica()){
			//Representantes de la persona jurídica
			for(RepresentantePersonaJuridica r : solicitante.representantes){
				String cardinalidad = null;
				if(r.tipoRepresentacion.equals("mancomunado")){
					cardinalidad = "multiple";
				}else if((r.tipoRepresentacion.equals("solidario")) || (r.tipoRepresentacion.equals("administradorUnico"))){
					cardinalidad = "unico";
				}
				Firmante firmante = new Firmante(r, "representante", cardinalidad);
				firmantes.add(firmante);
			}
		}
	}
	*/	
	
}
