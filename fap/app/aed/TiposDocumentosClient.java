package aed;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javassist.NotFoundException;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;

import org.apache.log4j.Logger;

import messages.Messages;
import models.ObligatoriedadDocumentos;
import models.Quartz;
import models.Singleton;
import models.TableKeyValue;

import platino.PlatinoProxy;
import play.db.jpa.JPABase;
import play.db.jpa.JPAPlugin;
import play.test.Fixtures;
import properties.FapProperties;
import tags.StringUtils;

import es.gobcan.eadmon.aed.ws.Aed;
import es.gobcan.eadmon.aed.ws.AedPortType;
import es.gobcan.eadmon.gestordocumental.ws.tiposdocumentos.TiposDocumentos;
import es.gobcan.eadmon.gestordocumental.ws.tiposdocumentos.TiposDocumentosExcepcion;
import es.gobcan.eadmon.gestordocumental.ws.tiposdocumentos.TiposDocumentosInterface;
import es.gobcan.eadmon.gestordocumental.ws.tiposdocumentos.dominio.Grupo;
import es.gobcan.eadmon.gestordocumental.ws.tiposdocumentos.dominio.GrupoTipoDocumento;
import es.gobcan.eadmon.gestordocumental.ws.tiposdocumentos.dominio.ListaGrupoTipoDocumento;
import es.gobcan.eadmon.gestordocumental.ws.tiposdocumentos.dominio.TipoDocumento;
import es.gobcan.eadmon.procedimientos.ws.Procedimientos;
import es.gobcan.eadmon.procedimientos.ws.ProcedimientosExcepcion;
import es.gobcan.eadmon.procedimientos.ws.ProcedimientosInterface;
import es.gobcan.eadmon.procedimientos.ws.dominio.AportadoPorEnum;
import es.gobcan.eadmon.procedimientos.ws.dominio.ListaProcedimientos;
import es.gobcan.eadmon.procedimientos.ws.dominio.ListaTiposDocumentosEnTramite;
import es.gobcan.eadmon.procedimientos.ws.dominio.ListaTramites;
import es.gobcan.eadmon.procedimientos.ws.dominio.ObligatoriedadEnum;
import es.gobcan.eadmon.procedimientos.ws.dominio.Procedimiento;
import es.gobcan.eadmon.procedimientos.ws.dominio.TipoDocumentoEnTramite;
import es.gobcan.eadmon.procedimientos.ws.dominio.Tramite;
import es.gobcan.eadmon.procedimientos.ws.servicios.ObtenerTramite;
import es.gobcan.eadmon.verificacion.ws.dominio.ListaDocumentosVerificacion;

/**
 * @deprecated Utilizar tiposDocumentoService o procedimientosService con la nueva forma de inyectar dependencias
 */
@Deprecated
public class TiposDocumentosClient {

	private static TiposDocumentosInterface tipos;
	private static ProcedimientosInterface procedimientos;
	private static Logger log = Logger.getLogger(TiposDocumentosClient.class);
	static {		
		URL wsdlTipoURL = Aed.class.getClassLoader().getResource ("wsdl/tipos-documentos/tipos-documentos.wsdl");
		tipos = new TiposDocumentos(wsdlTipoURL).getTiposDocumentos();
		
		BindingProvider bpTipo = (BindingProvider) tipos;
		bpTipo.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, FapProperties.get("fap.aed.tiposdocumentos.url"));
		
		PlatinoProxy.setProxy(tipos);
		
		//procedimientos
		URL wsdlProcedimientosURL = Aed.class.getClassLoader().getResource ("wsdl/procedimientos/procedimientos.wsdl");
		procedimientos = new Procedimientos(wsdlProcedimientosURL).getProcedimientos();
		
		BindingProvider bpProcedimientos = (BindingProvider) procedimientos;
		bpProcedimientos.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, FapProperties.get("fap.aed.procedimientos.url"));
		
		PlatinoProxy.setProxy(procedimientos);
	}

	public static String getVersion() throws Exception {
		Holder<String> h1 = new Holder<String>();
		Holder<String> h2 = new Holder<String>();
		tipos.obtenerVersionServicio(h1, h2);
		procedimientos.obtenerVersionServicio(h1, h2);
		return h1.value;
	}
	
	
	public static boolean actualizarTramites() {
		String uriProcedimiento = FapProperties.get("fap.aed.procedimientos.procedimiento.uri"); 
		JPAPlugin.startTx(false);

		try {
			//Borra los trámites antiguos
			Fixtures.delete(models.Tramite.class);
			
			//Recupera los trámites y los tipos de documentos asociados
			ListaTramites tramites = procedimientos.consultarTramites(uriProcedimiento);
			for (Tramite tramite : tramites.getTramites()) {				
				models.Tramite tramitedb = new models.Tramite();
				tramitedb.uri = tramite.getUri();
				tramitedb.nombre = tramite.getNombre();
				
				List<TipoDocumentoEnTramite> documentos = procedimientos.
								consultarTiposDocumentosEnTramite(uriProcedimiento, tramite.getUri()).getTiposDocumentos();
				
				for(TipoDocumentoEnTramite tipoDocumento : documentos){
					models.TipoDocumento tipoDocumentoDb  = new models.TipoDocumento();
					
					tipoDocumentoDb.uri = tipoDocumento.getUri();
					tipoDocumentoDb.aportadoPor = tipoDocumento.getAportadoPor().toString();
					tipoDocumentoDb.obligatoriedad = tipoDocumento.getObligatoriedad().toString();
					
					//Consulta al WS de Tipos de Documentos la descripción
					TipoDocumento td = tipos.obtenerTipoDocumento(tipoDocumento.getUri());
					tipoDocumentoDb.nombre = td.getDescripcion();	
					
					tramitedb.documentos.add(tipoDocumentoDb);
				}
				
				tramitedb.save();
			}
			
			//Añade el tipo y la descripción a la tabla de tablas
			List<models.TipoDocumento> tiposDocumentos = models.TipoDocumento.findAll();
			String table = "tiposDocumentos";
			TableKeyValue.deleteTable(table);
			for(models.TipoDocumento tipo : tiposDocumentos){
				TableKeyValue.setValue(table, tipo.uri, tipo.nombre, false);
			}
			TableKeyValue.renewCache(table); //Renueva la cache una única vez
			
		} catch (ProcedimientosExcepcion e) {
			aedError("Se produjo un error en el servicio web de Procedimientos"+ uriProcedimiento, e);
			JPAPlugin.closeTx(true);
			return false;
		} catch(TiposDocumentosExcepcion e){
			aedError("Se produjo un error en el servicio web de TiposDocumenetos"+ uriProcedimiento, e);
			JPAPlugin.closeTx(true);
			return false;
		}
		JPAPlugin.closeTx(false);
		return true;
	}
	
	private static void aedError(String error, ProcedimientosExcepcion e){
		aedError(error, e.getFaultInfo().getDescripcion());
	}
	
	private static void aedError(String error, TiposDocumentosExcepcion e){
		aedError(error, e.getFaultInfo().getDescripcion());
	}
	
	private static void aedError(String error, String descripcion){
		log.error(error + " - descripcion: "+ descripcion);
		Messages.error(error);		
	}
	
	
	/**
	 * Se actualizará la lista de tipos de documentos para cada trámite.
	 * 
	 * Ejemplo: Para el tramite "solicitud", se actualizarán las listas "tipoDocumentosCiudadanosSolicitud"
	 * 
	 * @return
	 */
	public static boolean actualizarTiposDocumentoDB() {
		play.Logger.info("Actualizando los Tipos de Documentos en lA BBDD");
		//String uriTramite = FapProperties.get("fap.aed.procedimientos.tramite.uri");
		String uriProcedimiento = FapProperties.get("fap.aed.procedimientos.procedimiento.uri");
		
		//Recupera los trámites y los tipos de documentos asociados
		ListaTramites tramites = null;
		try {
			tramites = procedimientos.consultarTramites(uriProcedimiento);
		} catch (ProcedimientosExcepcion e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		boolean result = true;
		for (Tramite tramite : tramites.getTramites()) {
			play.Logger.info("Trámite: "+tramite.getNombre());
				
			List<TipoDocumentoEnTramite> listaTodos = new ArrayList<TipoDocumentoEnTramite>();
			List<TipoDocumentoEnTramite> listaCiudadanos = new ArrayList<TipoDocumentoEnTramite>();
			List<TipoDocumentoEnTramite> listaOrganismos = new ArrayList<TipoDocumentoEnTramite>();
			List<TipoDocumentoEnTramite> listaOtrasEntidades = new ArrayList<TipoDocumentoEnTramite>();

			try {
				listaTodos = procedimientos.consultarTiposDocumentosEnTramite(uriProcedimiento, tramite.getUri()).getTiposDocumentos();
			}catch(Exception e){
				log.error("Se produjo un error al consultar los tipos de Documentos", e);
				return false;
			}
		
			for (TipoDocumentoEnTramite tipoDoc : listaTodos) {
				if (tipoDoc.getAportadoPor() == AportadoPorEnum.CIUDADANO) {
					listaCiudadanos.add(tipoDoc);
				}else if (tipoDoc.getAportadoPor() == AportadoPorEnum.ORGANISMO) {
					listaOrganismos.add(tipoDoc);				
				}else if (tipoDoc.getAportadoPor() == AportadoPorEnum.OTRAS_ENTIDADES) {
					listaOtrasEntidades.add(tipoDoc);				
				}				
			}
		
			boolean todos = actualizarDocumentosDB(listaTodos, "tipoDocumentosTodos");
			boolean ciudadano = actualizarDocumentosDB(listaCiudadanos, "tipoDocumentosCiudadanos"+StringUtils.firstUpper(tramite.getNombre()));
			boolean organismo = actualizarDocumentosDB(listaOrganismos, "tipoDocumentosOrganismos"+StringUtils.firstUpper(tramite.getNombre()));
			boolean otrasEntidades = actualizarDocumentosDB(listaOtrasEntidades, "tipoDocumentosOtrasEntidades"+StringUtils.firstUpper(tramite.getNombre()));
		
			boolean obligatoriedad = actualizarObligatoriedadDocumentos(listaCiudadanos);
		
			result = result && todos && ciudadano && organismo && otrasEntidades && obligatoriedad;
		}
		return result;
	}
	
	public static boolean actualizarDocumentosDB(List<TipoDocumentoEnTramite> lista, String table) {
		JPAPlugin.startTx(false);
		TableKeyValue.deleteTable(table);		
		try {
			for(TipoDocumentoEnTramite tipoDoc : lista){
				String uriTipoDocumento = tipoDoc.getUri();
				TipoDocumento tipoDocumento = tipos.obtenerTipoDocumento(uriTipoDocumento);
				TableKeyValue.setValue(table, uriTipoDocumento, tipoDocumento.getDescripcion(), false);
			}
		}catch(Exception e){
			log.error("Se produjo un error actualizando la tabla " + table + ". Rollback", e);
			JPAPlugin.closeTx(true);
			return false;
		}
		TableKeyValue.renewCache(table); //Renueva la cache
		JPAPlugin.closeTx(false);
		log.debug("lista de documentos de la tabla " + table + " actualizada");
		return true;
	}

	
	public static boolean actualizarObligatoriedadDocumentos(List<TipoDocumentoEnTramite> lista) {
		JPAPlugin.startTx(false);
    	ObligatoriedadDocumentos docObli = ObligatoriedadDocumentos.get(ObligatoriedadDocumentos.class);
		try {
			docObli.clear();
			for(TipoDocumentoEnTramite tipoDoc : lista){
				ObligatoriedadEnum obligatoriedad = tipoDoc.getObligatoriedad();
				if (obligatoriedad == ObligatoriedadEnum.IMPRESCINDIBLE) {
					docObli.imprescindibles.add(tipoDoc.getUri());
				}
				else if (obligatoriedad == ObligatoriedadEnum.OBLIGATORIO) {
					docObli.obligatorias.add(tipoDoc.getUri());
				}
				else if (obligatoriedad == ObligatoriedadEnum.CONDICIONADO_AUTOMATICO) {
					docObli.automaticas.add(tipoDoc.getUri());
				}
				else if (obligatoriedad == ObligatoriedadEnum.CONDICIONADO_MANUAL) {
					docObli.manuales.add(tipoDoc.getUri());
				}
				else {
					throw new NotFoundException("Tipo de obligatoriedad no encontrado ("+obligatoriedad.name()+")");
				}
			}
			docObli.save();
		}catch(Exception e){
			log.error("Se produjo un error al asignar el tipo de obligatoriedad de los documentos", e);
			JPAPlugin.closeTx(true);
			return false;
		}
		JPAPlugin.closeTx(false);
		return true;
	}
	
	// Más funciones que se han pedido
	
	public static List<TipoDocumentoEnTramite> getTiposDocumentosEnTramite(String uriProcedimiento, String uriTramite) {
        List<TipoDocumentoEnTramite> result = new ArrayList<TipoDocumentoEnTramite>();
        try {
            result = procedimientos.consultarTiposDocumentosEnTramite(uriProcedimiento, uriTramite).getTiposDocumentos();
        } catch (Exception ex) {
            log.error("Se produjo un error al consultar los tipos de documentos.", ex);
        }
        return result;
    }
	
	 public static List<Procedimiento> getProcedimientos() {
	    List<Procedimiento> result = new ArrayList<Procedimiento>();
	    try {
	    	ListaProcedimientos serviceList = procedimientos.consultarProcedimientos();
	        if (serviceList != null) {
	        	result = serviceList.getProcedimientos();
	        }
	    } catch (Exception ex) {
	        log.error("Se produjo un error al consultar los tipos de procedimientos.", ex);
	    }
        return result;
    }

	 public static List<Tramite> getTramitesDeProcedimiento(String uriProcedimiento) {
        List<Tramite> result = new ArrayList<Tramite>();
        try {
            ListaTramites servicesList = procedimientos.consultarTramites(uriProcedimiento);
            if (servicesList != null) {
                result = servicesList.getTramites();
            }
        } catch (Exception ex) {
            log.error("Se produjo un error al consultar los tipos de trámites.", ex);
        }
        return result;
	 }
	 
	 public static TipoDocumento getTipoDocumento(String uriDocumento) throws TiposDocumentosExcepcion {
        return tipos.obtenerTipoDocumento(uriDocumento);
	 }

}
