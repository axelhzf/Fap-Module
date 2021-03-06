package es.fap.simpleled.led.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

import es.fap.simpleled.led.*;

public class LedCampoUtils {
	
	public static Entity getUltimaEntidad(Campo campo){
		if (campo == null){
			return null;
		}
		Entity result = campo.getEntidad();
		CampoAtributos attrs = campo.getAtributos();
		while (attrs != null){
			Attribute attr = attrs.getAtributo();
			if (LedEntidadUtils.getEntidad(attr) != null){
				result = LedEntidadUtils.getEntidad(attr);
			}
			attrs = attrs.getAtributos();
		}
		return result;
	}
	
	public static Attribute getUltimoAtributo(Campo campo){
		Attribute result = null;
		CampoAtributos attrs = campo.getAtributos();
		while (attrs != null){
			result = attrs.getAtributo();
			attrs = attrs.getAtributos();
		}
		return result;
	}
	
	public static CampoAtributos getUltimoCampoAtributos(Campo campo){
		if (campo.getAtributos() == null){
			return null;
		}
		CampoAtributos attrs = campo.getAtributos();
		while (attrs.getAtributos() != null){
			attrs = attrs.getAtributos();
		}
		return attrs;
	}
	
	@SuppressWarnings("unchecked")
	public static EList<Elemento> getElementos(EObject container){
		try {
			return (EList<Elemento>) container.getClass().getMethod("getElementos").invoke(container);
		} catch (Exception e) {
			return null;
		}
	}
	
	public static boolean equals(Campo campo1, Campo campo2){
		if (campo1 == null || campo2 == null)
			return campo1 == campo2;
		if (!campo1.getEntidad().getName().equals(campo2.getEntidad().getName()))
			return false;
		CampoAtributos atributos1 = campo1.getAtributos();
		CampoAtributos atributos2 = campo2.getAtributos();
		while (atributos1 != null && atributos2 != null){
			if (!atributos1.getAtributo().getName().equals(atributos2.getAtributo().getName()))
				return false;
			atributos1 = atributos1.getAtributos();
			atributos2 = atributos2.getAtributos();
		}
		return atributos1 == atributos2;
	}
	
	public static boolean hayCamposGuardables(EObject container){
		EList<Elemento> elementos = getElementos(container);
		if (elementos != null){
			for (EObject obj: elementos){
				if (hayCamposGuardables(obj))
					return true;
			}
			return false;
		}
		return (!((container instanceof Tabla) || (container instanceof FirmaPlatinoSimple))) && getCampo(container) != null;
	}
	
	public static boolean hayCamposGuardablesOrTablaOneToMany(EObject container){
		EList<Elemento> elementos = getElementos(container);
		if (elementos != null){
			for (EObject obj: elementos){
				if (hayCamposGuardablesOrTablaOneToMany(obj))
					return true;
			}
			return false;
		}
		if (container instanceof Tabla)
			return LedCampoUtils.getUltimoAtributo(getCampo(container)) != null;
		return getCampo(container) != null;
	}
	
	public static Campo getCampo(EObject object) {
		if (object instanceof Campo) 
			return (Campo) object;
		for (Method method : object.getClass().getMethods()) {
			if (method.getReturnType().equals(Campo.class)) {
				try {
					return (Campo) method.invoke(object);
				} catch (Exception e) {
					return null;
				}
			}
		}
		return null;
	}
	
	public static Campo getCampoContainer(CampoAtributos atributos){
		while (atributos.eContainer() instanceof CampoAtributos){
			atributos = (CampoAtributos) atributos.eContainer();
		}
		return (Campo) atributos.eContainer();
	}
	
	public static EObject getElementosContainer(EObject obj){
		EObject container = obj.eContainer();
		if (obj instanceof Campo)
			container = container.eContainer();
		while (container != null){
			if (container instanceof Tabla || container instanceof Pagina || container instanceof Popup || container instanceof Model){
				return container;
			}
			container = container.eContainer();
		}
		return null;
	}
	
	public static boolean validCampo(Campo campo){
		if (campo.getMethod() != null)
			return true;
		if (campo.getEntidad() == null || campo.getEntidad().getName() == null){
			return false;
		}
		CampoAtributos atributos = campo.getAtributos();
		while (atributos != null){
			if (atributos.getAtributo() == null || atributos.getAtributo().getName() == null){
				return false;
			}
			atributos = atributos.getAtributos();
		}
		return true;
	}
	
	/*
	 * Devuelve el campo asociado a una página, que será:
	 * 		el definido en el elemento Pagina.
	 * 		ó: el definido en el elemento Formulario de la página.
	 * 		ó: null
	 */
	public static Campo getCampoPagina(Pagina pagina){
		if (pagina.getCampo() != null){
			return pagina.getCampo();
		}
		return ((Formulario) pagina.eContainer()).getCampo();
	}
	
	/*
	 * Devuelve cual es la entidad que se puede usar en ese campo (sin contar
	 * las Singleton), en función del contexto en que se sitúe dicho campo.
	 */
	public static Entity getEntidadPaginaOrPopupOrTabla(EObject element){
		EObject container = LedCampoUtils.getElementosContainer(element);
		if (container instanceof Pagina)
			return LedEntidadUtils.getEntidad((Pagina)container);
		if (container instanceof Tabla || container instanceof Popup)
			return LedCampoUtils.getUltimaEntidad(LedCampoUtils.getCampo(container));
		return null;
	}
	
	public static Map<String, Entity> getEntidadesValidas(EObject elemento){
		EObject container = LedCampoUtils.getElementosContainer(elemento);
		Map<String, Entity> entidades = new HashMap<String, Entity>();
		if (container instanceof Model || elemento instanceof Tabla){
			for (Entity e: ModelUtils.<Entity>getVisibleNodes(LedPackage.Literals.ENTITY, elemento.eResource()))
				entidades.put(e.getName(), e);	
			return entidades;
		}
		if (container instanceof Pagina){
			Entity entidadPagina = LedEntidadUtils.getEntidad((Pagina)container);
			if (entidadPagina != null)
				entidades.put(entidadPagina.getName(), entidadPagina);
		}
		if (container instanceof Tabla || container instanceof Popup){
			Entity ultimaEntidad = LedCampoUtils.getUltimaEntidad(LedCampoUtils.getCampo(container));
			entidades.put(ultimaEntidad.getName(), ultimaEntidad);
		}
		if (! (container instanceof Tabla)){
			for (Entity single: LedEntidadUtils.getSingletons(elemento.eResource()))
				entidades.put(single.getName(), single);
		}
		return entidades;
	}

	/*
	 * Concatena dos campos, siempre y cuando la ultima entidad del primer campo sea la misma
	 * que la primera entidad del segundo campo.
	 */
	public static Campo concatena(Campo primero, Campo segundo){
		if (primero == null && segundo == null)
			return null;
		if (primero == null && segundo != null)
			return segundo;
		if (primero != null && segundo == null)
			return primero;
		if (!LedEntidadUtils.equals(getUltimaEntidad(primero), segundo.getEntidad()))
			return null;
		Campo result = LedFactory.eINSTANCE.createCampo();
		result.setEntidad(primero.getEntidad());
		CampoAtributos atributos = primero.getAtributos();
		CampoAtributos attrs = null;
		while (atributos != null){
			if (attrs == null){
				result.setAtributos(LedFactory.eINSTANCE.createCampoAtributos());
				attrs = result.getAtributos();
			}
			else{
				attrs.setAtributos(LedFactory.eINSTANCE.createCampoAtributos());
				attrs = attrs.getAtributos();
			}
			attrs.setAtributo(atributos.getAtributo());
			atributos = atributos.getAtributos();
		}
		atributos = segundo.getAtributos();
		while (atributos != null){
			if (attrs == null){
				result.setAtributos(LedFactory.eINSTANCE.createCampoAtributos());
				attrs = result.getAtributos();
			}
			else{
				attrs.setAtributos(LedFactory.eINSTANCE.createCampoAtributos());
				attrs = attrs.getAtributos();
			}
			attrs.setAtributo(atributos.getAtributo());
			atributos = atributos.getAtributos();
		}
		return result;
	}
	
	/*
	 * campo = Solicitud.documentos, start = Solicitud ---> true
	 * campo = Solicitud.documentos, start = Documento ---> false
	 */
	public static boolean startsWith(Campo campo, Campo start){
		if (campo == null || start == null)
			return campo == start;
		if (!campo.getEntidad().getName().equals(start.getEntidad().getName()))
			return false;
		CampoAtributos campoAttrs = campo.getAtributos();
		CampoAtributos startAttrs = start.getAtributos();
		while (startAttrs != null){
			if (campoAttrs == null)
				return false;
			if (!campoAttrs.getAtributo().getName().equals(startAttrs.getAtributo().getName()))
				return false;
			campoAttrs = campoAttrs.getAtributos();
			startAttrs = startAttrs.getAtributos();
		}
		return true;
	}
	
	// Convierte un campo en su equivalente en String
	
	public static String getCampoStr(Campo campo){
		if (campo == null){
			return null;
		}
		String campoStr = campo.getEntidad().getName();
		if (campoStr.equals("SolicitudGenerica")){
			campoStr = "Solicitud";
		}
		CampoAtributos attrs = campo.getAtributos();
		while (attrs != null){
			campoStr += "." + attrs.getAtributo().getName();
			attrs = attrs.getAtributos();
		}
		return campoStr;
	}
	
	public static List<Campo> buscarCamposRecursivos (EObject container){
		EList<Elemento> elementos = LedCampoUtils.getElementos(container);
		List<Campo> campos = new ArrayList<Campo>();
		if (elementos != null){
			for (EObject obj: elementos){
				campos.addAll(buscarCamposRecursivos(obj));
			}
		} else {
			campos.add(LedCampoUtils.getCampo(container));
		}
		return campos;
	}
	
}
