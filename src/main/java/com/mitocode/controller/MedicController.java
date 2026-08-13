package com.mitocode.controller;

import com.mitocode.dto.MedicDTO;
import com.mitocode.model.Medic;
import com.mitocode.service.impl.MedicServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/medics")
@RequiredArgsConstructor
public class MedicController {

    private final MedicServiceImpl service;

    @Qualifier("medicMapper")
    private final ModelMapper modelMapper;

    @GetMapping
    public ResponseEntity<List<MedicDTO>> findAll(){
        List<MedicDTO> list = service.findAll().stream()
                .map(this::convertToDTO).toList();

        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicDTO> findById(@PathVariable("id") Integer id){
        Medic obj = service.findById(id);

        return ResponseEntity.ok(convertToDTO(obj));
    }

    @PostMapping
    public ResponseEntity<Void> save(@Valid @RequestBody MedicDTO dto){
        Medic obj = service.save(convertToEntity(dto));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(obj.getIdMedic()).toUri();

        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<MedicDTO> update(@Valid @PathVariable("id") Integer id, @RequestBody MedicDTO dto){
        dto.setIdMedic(id);
        Medic obj = service.update(id ,convertToEntity(dto));

        return ResponseEntity.ok(convertToDTO(obj));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Integer id){
        service.delete(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hateoas/{id}")
    public EntityModel<MedicDTO> findByIdHateoas(@PathVariable("id") Integer id){
        EntityModel<MedicDTO> resource = EntityModel.of(convertToDTO(service.findById(id)));
        WebMvcLinkBuilder link1 = linkTo(methodOn(this.getClass()).findById(id));
        WebMvcLinkBuilder link2 = linkTo(methodOn(this.getClass()).findAll());
        resource.add(link1.withRel("medic-info-byId"));
        resource.add(link2.withRel("medic-all-info"));

        return resource;
    }

    private MedicDTO convertToDTO(Medic obj){
        return modelMapper.map(obj, MedicDTO.class);
    }

    private Medic convertToEntity(MedicDTO dto){
        return modelMapper.map(dto, Medic.class);
    }

}
