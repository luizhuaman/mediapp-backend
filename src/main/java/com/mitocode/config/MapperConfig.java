package com.mitocode.config;

import com.mitocode.dto.MedicDTO;
import com.mitocode.model.Medic;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {

    @Bean("defaultMapper")
    public ModelMapper defaultMapper() {
        return new ModelMapper();
    }

    @Bean("medicMapper")
    public ModelMapper medicMapper() {
        ModelMapper mapper = new ModelMapper();

        //Escritura
        TypeMap<MedicDTO, Medic> typeMap1 = mapper.createTypeMap(MedicDTO.class, Medic.class);
        typeMap1.addMapping(MedicDTO::getPrimaryName, (dest, v)-> dest.setFirstName((String) v));
        typeMap1.addMapping(MedicDTO::getSurname, (dest, v)-> dest.setLastName((String) v));
        typeMap1.addMapping(MedicDTO::getPhoto, (dest, v)-> dest.setPhotoUrl((String) v));
        //Lectura
        TypeMap<Medic, MedicDTO> typeMap2 = mapper.createTypeMap(Medic.class, MedicDTO.class);
        typeMap2.addMapping(Medic::getFirstName, (dest, v)-> dest.setPrimaryName((String) v));
        typeMap2.addMapping(Medic::getLastName, (dest, v)-> dest.setSurname((String) v));

        return mapper;
    }
}
