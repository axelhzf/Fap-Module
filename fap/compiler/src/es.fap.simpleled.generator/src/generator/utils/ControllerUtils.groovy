package generator.utils


import java.util.Map;
import java.util.ArrayList;

import es.fap.simpleled.led.*
import es.fap.simpleled.led.util.LedCampoUtils;
import es.fap.simpleled.led.util.LedEntidadUtils;

import org.eclipse.emf.ecore.EObject;

import com.sun.media.sound.RealTimeSequencer.PlayThread;

import utils.*;

class ControllerUtils {

	static List<String> camposSolicitante = "tipo,fisica.nombre,fisica.primerApellido,fisica.segundoApellido,fisica.nip,juridica.cif,juridica.entidad,representado".split(',')
	static List<String> camposSolicitantePersonaFisica = "tipo,fisica.nombre,fisica.primerApellido,fisica.segundoApellido,fisica.nip,representado".split(',');
	static List<String> camposSolicitantePersonaJuridica = "tipo,juridica.cif,juridica.entidad,representado".split(',');
	static List<String> camposPersona = "tipo,fisica.nombre,fisica.primerApellido,fisica.segundoApellido,fisica.nip,juridica.cif,juridica.entidad".split(',')
	static List<String> camposPersonaFisica = "nombre,primerApellido,segundoApellido,nip".split(",")
	static List<String> camposPersonaJuridica = "cif,entidad".split(',')
	static List<String> camposAed = "tipo,descripcion".split(",")
		
	static Stack<Set<String>> validatedFields;
	
    /**
     * Crea un getter que recupera una entidad desde base de datos por el ID
     * El id es obligatorio
     * @param entidad
     * @return
     */
    public static String simpleGetter(EntidadUtils entidad, boolean byId) {
		if (entidad.entidad == null){
			return "";
		}
		if (entidad.isSingleton()){
			return """
				@Util
				public static ${entidad.clase} get${entidad.clase}(){
					return ${entidad.clase}.get(${entidad.clase}.class);
				}
			"""
		}
		if (byId){
			return """
				@Util
				public static ${entidad.clase} get${entidad.clase}(${entidad.typeId}){
					${entidad.clase} ${entidad.variable} = null;
					if(${entidad.id} == null){
						if (!Messages.messages(MessageType.FATAL).contains("Falta parámetro ${entidad.id}"))
							Messages.fatal("Falta parámetro ${entidad.id}");
					}
					else{
						${entidad.variable} = ${entidad.clase}.findById($entidad.id);
						if($entidad.variable == null){
							Messages.fatal("Error al recuperar ${entidad.clase}");
						}
					}
					return ${entidad.variable};
				}
			"""
		}
		return """
			@Util
			public static ${entidad.clase} get${entidad.clase}(){
				return new ${entidad.clase}();
			}
		"""
    }

    public static String simpleGetterCall(String controllerName, EntidadUtils entidad, boolean byId) {
		if (byId && !entidad.isSingleton()){
			return "${controllerName}.get${entidad.clase}(${entidad.id})";
		}
		else{
			return "${controllerName}.get${entidad.clase}()";
		}
    }
	
	public static String newCall(EntidadUtils entidad) {
		if (entidad.entidad == null){
			return "";
		}
		if (entidad.isSingleton()){
			return "$entidad.variable = ${entidad.clase}.get(${entidad.clase}.class);"
		}
		else{
			return "$entidad.variable = new $entidad.clase();";
		}
	}

    /**
     * Crea un getter, para una entidad que tiene que buscar en una lista
     * El id de las dos entidades es obligatorio
     * @param almacen
     * @param entidad
     * @return
     */
    public static String complexGetter(String controllerName, EntidadUtils almacen, EntidadUtils entidad, CampoUtils campo) {
        if (almacen?.entidad == null)
            return simpleGetter(entidad, true);
		String singleton = "";
		EntidadUtils almacenNoSingle = EntidadUtils.create(almacen.entidad);
		if (almacen.isSingleton()){
			singleton = "${almacen.typeId} = ${simpleGetterCall(controllerName, almacen,false)}.id;";
			almacenNoSingle.entidad = null;
		}
        return """
	@Util
    public static ${entidad.clase} get${entidad.clase}(${StringUtils.params(almacenNoSingle.typeId, entidad.typeId)}){
        ${entidad.clase} ${entidad.variable} = null;
		${singleton}
        if(${almacen.id} == null){
			if (!Messages.messages(MessageType.FATAL).contains("Falta parámetro $almacen.id"))
            	Messages.fatal("Falta parámetro $almacen.id");
        }
		if($entidad.id == null){
			if (!Messages.messages(MessageType.FATAL).contains("Falta parámetro $entidad.id"))
            	Messages.fatal("Falta parámetro $entidad.id");
        }
		if(${almacen.id} != null && $entidad.id != null){
            $entidad.variable = ${entidad.clase}.find("select $entidad.variable from $almacen.clase $almacen.variable join ${campo.firstLower()} $entidad.variable where ${almacen.variable}.id=? and ${entidad.variable}.id=?", $almacen.id, $entidad.id).first();
            if($entidad.variable == null){
                Messages.fatal("Error al recuperar ${entidad.clase}");
            }
        }
        return $entidad.variable;
    }
        """
    }

    public static String complexGetterCall(String controllerName, EntidadUtils almacen, EntidadUtils entidad) {
        if (almacen?.entidad == null || almacen.isSingleton())
            return simpleGetterCall(controllerName, entidad, true);
        return "${controllerName}.get${entidad.clase}(${almacen.id}, ${entidad.id})";
    }

	public static String almacenGetterCall(String controllerName, AlmacenEntidad almacenEntidad) {
		if (almacenEntidad.almacen == null)
			return "";
		String clase = "${almacenEntidad.almacen.clase} ${almacenEntidad.almacen.variableDb} = ";
		if (almacenEntidad.almacenAnterior.nulo())
			return "${clase} ${simpleGetterCall(controllerName, almacenEntidad.almacen, true)};";
		return "${clase} ${complexGetterCall(controllerName, almacenEntidad.almacenAnterior, almacenEntidad.almacen)};";
	}

    public static String listGetterCall(String controllerName, Object ... entities) {
		return """
			${getEntityList(entities).collect{"$it.clase $it.variableDb = ${ControllerUtils.simpleGetterCall(controllerName, it, false)};"}.join("\n")}
		""";
    }

	public static String validateCopyMethod(gElemento, Object ... entities){
		if (entities.length == 0)
			return "";
		def params = [];
		validatedFields = new Stack<Set<String>>();
		validatedFields.push(new HashSet<String>());
		getEntityList(entities).each { entidad ->
			params.add(entidad.typeDb);
			params.add(entidad.typeVariable);
		}
		return """
			@Util
			public static void ${gElemento.name}ValidateCopy(String accion, ${StringUtils.params(params, gElemento.saveExtra)}){
				CustomValidation.clearValidadas();
				${validateCopy(gElemento.elementoGramatica)}
				${gElemento.saveCode.collect{ it.saveCode() }.join(";")}
			}
		"""
	}
	
    public static String validateCopyCall(String controllerName, String accion, gElemento, Object ... entities){
		if (entities.length == 0)
			return "";
		def params = [];
		getEntityList(entities).each { entidad ->
			params.add(entidad.variableDb);
			params.add(entidad.variable);
		}
		return """${controllerName}.${gElemento.name}ValidateCopy(${StringUtils.params(accion, params, gElemento.saveExtra.collect{it.split(" ")[1]}.unique())});""";
    }

	public static String bindReferencesCall(String controllerName, gElemento, Object ... entities){
		if (entities.length == 0)
			return "";
		def params = [];
		getEntityList(entities).each { entidad -> params.add(entidad.variable); }
		return """${controllerName}.${gElemento.name}BindReferences(${StringUtils.params(params, gElemento.saveExtra.collect{it.split(" ")[1]}.unique())});""";
	}

    private static String validateCopy(objeto) {
        String out = "";
        if ((Pagina.class.isInstance(objeto)) || (Grupo.class.isInstance(objeto)) || (Popup.class.isInstance(objeto)) || Form.class.isInstance(objeto) || EntidadAutomatica.class.isInstance(objeto)) {
			
			if (Grupo.class.isInstance(objeto)) {
				// Si es un grupo debemos incluir lood IF de los mostrarSiCheck, mostrarSiCampo, ...
				if (objeto.siCombo != null) {
					if (Combo.class.isInstance(objeto.siCombo)) {
						CampoUtils campo = CampoUtils.create(objeto.siCombo.campo);
						out += """if (Arrays.asList(new String[] {${objeto.siComboValues.values.collect{"\"${it}\""}.join(',')}}).contains(${campo.dbStr()})){\n""";
					}
				}
				else if (objeto.siCheck != null) {
					CampoUtils campo = CampoUtils.create(objeto.siCheck.campo);
					out += "if ((${campo.firstLower()} != null) && (${campo.firstLower()} == ${objeto.siCheckValues})) {\n";
				}
				else if (objeto.campo != null) {
					CampoUtils campo = CampoUtils.create(objeto.campo);
					out += """if (Arrays.asList(new String[] {${objeto.siCampoValues.values.collect{"\"${it}\""}.join(',')}}).contains(${campo.dbStr()})){\n""";
				}
				else if (objeto.siExpresion != null) {
					out += "if (${objeto.siExpresion}) {"
				}
			}
			
			if (objeto.permiso != null){
                out += """if (secure.checkGrafico("${objeto.permiso.name}", "editable", accion, (Map<String,Long>)tags.TagMapStack.top("idParams"), null)) {\n"""
				validatedFields.push(new HashSet<String>());
			}
					
			for (Elemento elemento: objeto.elementos) {
				out += validateCopy(elemento);
			}
			
			if (Grupo.class.isInstance(objeto)) {
				// Si es un grupo debemos incluir lood IF de los mostrarSiCheck, mostrarSiCampo, ...
				if (objeto.siCombo != null) {
					if (Combo.class.isInstance(objeto.siCombo)) {
						out += "\n}";
					}
				}
				if ((objeto.siCheck != null) || (objeto.campo != null) || (objeto.siExpresion != null)) {
					out += "\n}"
				}
			}
			
            if (objeto.permiso != null) {
                out += "\n}\n"
				validatedFields.pop();
			}
        }
		else if (objeto.metaClass.respondsTo(objeto, "getCampo")) {
            if (objeto.campo != null) {
				if (objeto instanceof Solicitante) {
					CampoUtils campo = CampoUtils.create(objeto.campo);
					if (((Solicitante) objeto).representantePersonaFisica){
						out += """${campo.firstLower()}.representante.tipo = "fisica";
						"""
					}
					if (((Solicitante) objeto).elemento == "SolicitantePersonaFisica"){
						out += """${campo.firstLower()}.tipo = "fisica";
						"""
					}
					else if (((Solicitante) objeto).elemento == "SolicitantePersonaJuridica"){
						out += """${campo.firstLower()}.tipo = "juridica";
						"""
					}
				}
                out += validate(objeto);
                out += copy(objeto);
            }
        }
        return out;
    }

	public static String bindReferencesMethod(gElemento, Object ... entities){
		if (entities.length == 0)
			return "";
		def params = [];
		getEntityList(entities).each { entidad -> params.add(entidad.typeVariable); }
		return """
			@Util
			public static void ${gElemento.name}BindReferences(${StringUtils.params(params, gElemento.saveExtra)}){
				${bindReferences(gElemento.elementoGramatica)}
			}
		"""
	}
	
	private static String bindReferences(objeto) {
		String out = "";
		if ((Pagina.class.isInstance(objeto)) || (Grupo.class.isInstance(objeto)) || (Popup.class.isInstance(objeto)) || Form.class.isInstance(objeto) || EntidadAutomatica.class.isInstance(objeto)) {
			for (Elemento elemento: objeto.elementos)
				out += bindReferences(elemento);
		}
		else if (objeto.metaClass.respondsTo(objeto, "getCampo")) {
			if (objeto.campo != null)
				out += bindReference(objeto);
		}
		return out;
	}
	
    /**
     * Devuelve el codigo de copia de un elemento de la pagina, grupo o popUp
     * @param objeto
     * @return
     */
    public static String bindReference(EObject objeto) {
        if ((Grupo.class.isInstance(objeto)) || (Pagina.class.isInstance(objeto))
                || (Popup.class.isInstance(objeto)) || (Wiki.class.isInstance(objeto))
                || (Tabla.class.isInstance(objeto))) {
            return "";
        }

        if (objeto.campo == null)
            return "";
		CampoUtils campo = CampoUtils.create(objeto.campo);
		// Si la referencia es un ManyToOne o ManyToMany, solo igualamos ella, no sus campos
		if (LedEntidadUtils.isManyToOne(campo.getUltimoAtributo())) {
			return bindCampoMany2One(campo);
		} else if (LedEntidadUtils.isManyToMany(campo.getUltimoAtributo())) { 
			return bindCampoMany2Many(campo);
		}
		return "";
    }
	
	public static String bindCampoMany2One(CampoUtils campo) {
		String entity = campo.getUltimaEntidad().name;
		String str_ = campo.getStr_();
		return """
			String ${str_} = params.get("$str_");
			if ((${str_} != null) && (!${str_}.trim().equals(""))) {
				$entity ${str_}ctr = ${entity}.findById(Long.parseLong(${str_}.trim()));
				${campo.firstLower()} = ${str_}ctr;
			}
			else
				${campo.firstLower()} = null;
		""";
	}
	
	public static String bindCampoMany2Many(CampoUtils campo) {
		String entity = campo.getUltimaEntidad().name;
		String str_ = campo.getStr_();
		return """
			ArrayList<$entity> ${str_}aCT = new ArrayList<$entity>();
			String[] $str_ = params.getAll("$str_");
			if ($str_ != null) {
				for (String idString : $str_) {
					$entity ctr = ${entity}.findById(Long.parseLong(idString.trim()));
					${str_}aCT.add(ctr);
				}
			}
			${campo.firstLower()} = ${str_}aCT;
		""";
	}

	/**
	* Devuelve el codigo de copia de un elemento de la pagina, grupo o popUp
	* @param objeto
	* @return
	*/
   public static String copy(EObject objeto) {
	   if ((Grupo.class.isInstance(objeto)) || (Pagina.class.isInstance(objeto))
			   || (Popup.class.isInstance(objeto)) || (Wiki.class.isInstance(objeto))
			   || (Tabla.class.isInstance(objeto))) {
		   return "";
	   }

	   if (objeto.campo == null) {
		   return "";
	   }
	   CampoUtils campo = CampoUtils.create(objeto.campo);
	   
	   // Si la referencia es un ManyToOne o ManyToMany, solo igualamos ella, no sus campos
	   if (LedEntidadUtils.isManyToOne(campo.getUltimoAtributo())) {
		   return copyCampoMany2One(campo);
	   } else if (LedEntidadUtils.isManyToMany(campo.getUltimoAtributo())) {
		   return copyCampoMany2Many(campo);
	   }
	   String validOut = "";
	   List<String> camposFiltrados;
	   if (objeto instanceof Solicitante) {
		   if (((Solicitante) objeto).elemento == "Solicitante"){
			   camposFiltrados = camposSolicitante
			   if (!((Solicitante) objeto).isNoRepresentante()) {
				   validOut += """if (${campo.firstLower()}.isPersonaFisica()) {
				   """
				   validOut += copyRepresentanteFisica (campo.str)
				   if (!((Solicitante) objeto).representantePersonaFisica)
					   validOut += copyRepresentanteJuridica (campo.str)
				   validOut += """\n}""";
			   }
		   }
		   else if (((Solicitante) objeto).elemento == "SolicitantePersonaFisica"){
			   camposFiltrados = camposSolicitantePersonaFisica
			   if (!((Solicitante) objeto).isNoRepresentante()) {
				   validOut += """if (${campo.firstLower()}.isPersonaFisica()) {
				   """
				   validOut += copyRepresentanteFisica (campo.str)
				   if (!((Solicitante) objeto).representantePersonaFisica)
					   validOut += copyRepresentanteJuridica (campo.str)
				   validOut += """\n}""";
			   }
		   }
		   else if (((Solicitante) objeto).elemento == "SolicitantePersonaJuridica"){
			   camposFiltrados = camposSolicitantePersonaJuridica
			   if (!((Solicitante) objeto).isNoRepresentante()) {
				   validOut += """if (${campo.firstLower()}.isPersonaFisica()) {
				   """
				   validOut += copyRepresentanteFisica (campo.str)
				   if (!((Solicitante) objeto).representantePersonaFisica)
					   validOut += copyRepresentanteJuridica (campo.str)
				   validOut += """\n}""";
			   }
		   }
	   } else if (objeto instanceof Persona) {
		   camposFiltrados = camposPersona
	   } else if (objeto instanceof PersonaFisica) {
		   camposFiltrados = camposPersonaFisica
	   } else if (objeto instanceof PersonaJuridica) {
		   camposFiltrados = camposPersonaJuridica
	   } else if(objeto instanceof SubirArchivo){
		   camposFiltrados = camposAed
	   } else if(objeto instanceof EditarArchivo) {
		   camposFiltrados = camposAed
	   }

	   String out = "";
	   if (camposFiltrados == null) {
		   out = copyCamposTodos(campo);
	   } else {
		   out = copyCamposFiltrados(campo, camposFiltrados);
	   }
	   
	   return out + validOut;
   }
	
	public static String copyCamposTodos(CampoUtils campo) {
		Entity entidad;
		Attribute last = campo.getUltimoAtributo();
		if (last != null){
			if (LedEntidadUtils.xToMany(last)){
				return "";
			}
			entidad = LedEntidadUtils.getEntidad(last);
		}
		else{
			entidad = campo.entidad;
		}
		if (entidad != null){
			String out = "";
			for (Attribute at: LedEntidadUtils.getAllDirectAttributesExceptId(entidad)){
				out += copyCamposTodos(CampoUtils.create(campo.addAttribute(at)));
			}
			return out;
		}
		else{
			return copyCampoSimple(campo);
		}
	}
	
	public static String copyCamposFiltrados(CampoUtils campo, List<String> campos) {
		String out = "";
		for (String campoStr: campos){
			CampoUtils c = CampoUtils.create(campo.addMore(campoStr));
			if (c != null){
				out += copyCampoSimple(c);
			}
		}
		return out;
	}
	
	public static String copyCampoSimple(CampoUtils campo) {
		// SI el campo es un método, no hacemos nada
		if (campo.isMethod()) {
			return "";
		}
		if (LedEntidadUtils.isManyToOne(campo.getUltimoAtributo()))
			return copyCampoMany2One(campo);
		else if (LedEntidadUtils.isManyToMany(campo.getUltimoAtributo()))
			return copyCampoMany2Many(campo);
		else if (campo.getUltimoAtributo()?.type.compound?.multiple){
			return """
			db${campo.str}.retainAll(${campo.firstLower()});
			db${campo.str}.addAll(${campo.firstLower()});
			"""
		}
		// Para convertir el CIF o el NIP a Mayusculas, y guardarlo homogeneamente en la BBDD
		if ((campo != null) &&
			(campo.getUltimoAtributo() != null) &&
			(campo.getUltimoAtributo().getType() != null) &&
			(campo.getUltimoAtributo().getType().getCompound() != null) &&
			(campo.getUltimoAtributo().getType().getCompound().getEntidad() != null) &&
			(campo.getUltimoAtributo().getType().getCompound().getEntidad().getName().equals("Nip"))){
				String out = "${campo.firstLower()}.valor = ${campo.firstLower()}.valor.toUpperCase();\n";
				out += "db${campo.str} = ${campo.firstLower()};\n";
				return out;
		}
		if (LedEntidadUtils.getSimpleTipo(campo.getUltimoAtributo()).equals("Cif")){
				return "db${campo.str} = ${campo.firstLower()}.toUpperCase();\n";
		}
		return "db${campo.str} = ${campo.firstLower()};\n";
	}
	
	/**
	 * Realiza la copia de los campos Many2One. Cambiada por el problema de los IDs.
	 * @param campo
	 * @return
	 */
	public static String copyCampoMany2One(CampoUtils campo) {
		return "db${campo.str} = ${campo.firstLower()};";
	}
	
	/**
	* Realiza la copia de los campos Many2Many. Cambiada por el problema de los IDs.
	* @param campo
	* @return
	*/
	public static String copyCampoMany2Many(CampoUtils campo) {
		return """
			db${campo.str}.retainAll(${campo.firstLower()});
			db${campo.str}.addAll(${campo.firstLower()});
		"""
	}
   
	/**
	 * Codigo de copia y validación si el representante es una persona fisica
	 * @param campo
	 * @return
	 */
	public static String copyRepresentanteFisica (String campo) {
		return copyRepresentanteFisica (campo, "");
	}
	
	/**
	 * Código del representante si es una persona fisica (perteneciente a persona)
	 * @param campo
	 * @param persona String indicando el campo partir de "campo" anterior donde está la persona física
	 * @return
	 */
	public static String copyRepresentanteFisica (String campo, String persona) {
		def camposFJ = camposPersonaFisica
		String campol = StringUtils.firstLower(campo);
		String validOut = """
			// Para un representante físico
			if ((${campol}${persona}.representado != null) && (${campol}${persona}.representado) && (${campol}${persona}.representante.isPersonaFisica())) {
			"""
		validOut += "db${campo}${persona}.representante.tipo = ${campol}${persona}.representante.tipo;"
		validOut += "if (db${campo}${persona}.representante == null) db${campo}${persona}.representante = ${campol}${persona}.representante;\n"
		camposFJ.each {field -> validOut += "db${campo}${persona}.representante.fisica.${field} = ${campol}${persona}.representante.fisica.${field};\n"}
		validOut += "\n}";
	}
	
	/**
	 * Código del representante si es una persona jurídica
	 * @param campo
	 * @param persona
	 * @return
	 */
	public static String copyRepresentanteJuridica (String campo) {
		return copyRepresentanteJuridica (campo, "");
	}
	
	/**
	 * Código del representante si es una persona jurídica
	 * @param campo
	 * @return
	 */
	public static String copyRepresentanteJuridica (String campo, String persona) {
		def camposFJ = camposPersonaJuridica
		String campol = StringUtils.firstLower(campo);
		String validOut = """
			// Para un representante jurídico
			if ((${campol}${persona}.representado != null) && (${campol}${persona}.representado) && (${campol}${persona}.representante.isPersonaJuridica())) {
			"""
		validOut += "db${campo}${persona}.representante.tipo = ${campol}${persona}.representante.tipo;"
		validOut += "if (db${campo}${persona}.representante == null) db${campo}${persona}.representante = ${campol}${persona}.representante;\n"
		camposFJ.each {field -> validOut += "db${campo}${persona}.representante.juridica.${field} = ${campol}${persona}.representante.juridica.${field};\n"}
		validOut += "\n}";
	}
	
	
	public static String copyRepresentantes (String campo) {
		
	}
	
	/**
	* Indica si la entidad que se le pasa tiene check"Entity", con lo cual
	* se le deberá realizar el "validate"
	* @param entity
	* @return
	*/
   public static boolean isCheckEntity (EObject entity) {
	   if ((entity != null) && (
		   (entity instanceof Persona)
		   || (entity instanceof PersonaFisica)
		   || (entity instanceof PersonaJuridica)
		   || (entity instanceof Direccion)
		   || (entity instanceof Solicitante)
		   ))
		   return true;
	   return false;
   }
	
	/**
     * Devuelve el código de validación para un objeto
     * @param object
     * @return
     */
    public static String validate(objeto) {
        String out = "";
		
        if (objeto.metaClass.respondsTo(objeto, "getCampo") && (!Tabla.class.isInstance(objeto))) {
			String campo = CampoUtils.create(objeto.campo).str;
			String campol = StringUtils.firstLower(campo);
			
			if (CampoUtils.create(objeto.campo).isMethod()) {
				return "";
			}
			
			if (isCheckEntity(objeto)) {
                out += valid(campo);
            } else {
                // Debemos validar normalmente (sus entidades padre)
                int dotPlace = campol.length();
                dotPlace = campol.lastIndexOf('.', dotPlace - 1);
                while (dotPlace != -1) {
                    String lCampo = campol.substring(0, dotPlace);
                    out += valid(lCampo);
                    dotPlace = campol.lastIndexOf('.', dotPlace - 1);
                }
            }
			
			if ((objeto instanceof Solicitante)) {
                out += validValueFromTable(campo + ".tipo");
				// Debemos validar el representante de la persona física, si lo tiene
				out += """if (${campol}.isPersonaFisica() && (${campol}.representado != null) && (${campol}.representado)) {
					${required(campo + ".representante")}
					${valid(campo + ".representante")}
				}
				"""
			} else if ((objeto instanceof Persona)) {
                out += validValueFromTable(campo + ".tipo");
			} else if (objeto instanceof Direccion) {
                out += validValueFromTable(campo + ".municipio");
				if (objeto.isProvincia())
                	out += validValueFromTable(campo + ".provincia");
				if (objeto.isPais())
                	out += validValueFromTable(campo + ".pais");
			}
			if (objeto instanceof Combo){
				Combo combo = (Combo)objeto;
				if (LedCampoUtils.getUltimoAtributo(combo.campo).type.compound?.multiple){
					out += validListOfValuesFromTable(campo);
				}
				else{
					out += validValueFromTable(campo);
				}
			}
			if (objeto.metaClass.respondsTo(objeto, "isRequerido") && objeto.isRequerido()) {
				out += required(campo);
			}
			if (objeto.metaClass.respondsTo(objeto, "isDuplicar") && (objeto.isDuplicar())){
				out += duplicated(campo);
			}

        }
        return out;
    }
	
	private static String valid(String campo){
		campo = StringUtils.firstLower(campo);
		for (Set<String> set: validatedFields){
			if (set.contains(campo)){
				return "";
			}
		}
		validatedFields.peek().add(campo);
		return "CustomValidation.valid(\"${campo}\", ${campo});\n";
	}
	
	private static String required(String campo){
		campo = StringUtils.firstLower(campo);
		return "CustomValidation.required(\"${campo}\", ${campo});\n";
	}
	
	private static String validValueFromTable(String campo){
		campo = StringUtils.firstLower(campo);
		return "CustomValidation.validValueFromTable(\"${campo}\", ${campo});\n";
	}
	
	private static String validListOfValuesFromTable(String campo){
		campo = StringUtils.firstLower(campo);
		return "CustomValidation.validListOfValuesFromTable(\"${campo}\", ${campo});\n";
	}
	
	private static String duplicated (String campo){
		campo = StringUtils.firstLower(campo);
		String campo_ = campo.replaceAll("\\.", "_");
		return "CustomValidation.compare(${campo}, params.get(\"${campo_}copy\"));\n";
	}
	
	public static List<EntidadUtils> getEntityList(Object entities){
		def listEntities = [];
		entities.each { obj ->
			if (obj instanceof EntidadUtils && !obj.nulo())
				listEntities.add(obj);
			else if (obj instanceof List<EntidadUtils>)
				obj.each {e ->
					if (!e.nulo())
						listEntities.add(e);
				}
		}
		return listEntities;
	}
	
}
