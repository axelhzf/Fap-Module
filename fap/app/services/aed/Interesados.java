package services.aed;

import java.util.List;
import java.util.ArrayList;

import models.Persona;
import models.PersonaFisica;
import models.PersonaJuridica;

public class Interesados {

    private List<String> nombres = new ArrayList<String>();
    private List<String> documentos = new ArrayList<String>();
    
    public void add(Persona persona){
        if(persona == null || persona.getNombreCompleto() == null || persona.getNumeroId() == null)
            throw new NullPointerException();
        nombres.add(persona.getNombreCompleto());
        documentos.add(persona.getNumeroId());
    }
    
    public void add(PersonaFisica personaFisica){
        if(personaFisica == null || personaFisica.getNombreCompleto() == null || personaFisica.nip.valor == null)
            throw new NullPointerException();
        nombres.add(personaFisica.getNombreCompleto());
        documentos.add(personaFisica.nip.valor);        
    }
    
    public void add(PersonaJuridica personaJuridica){
        if(personaJuridica == null || personaJuridica.entidad == null || personaJuridica.cif == null)
            throw new NullPointerException();
        nombres.add(personaJuridica.entidad);
        documentos.add(personaJuridica.cif);
    }
    
    public List<String> getNombres(){
        return nombres;
    }
    
    public List<String> getDocumentos(){
        return documentos;
    }
    
 }
