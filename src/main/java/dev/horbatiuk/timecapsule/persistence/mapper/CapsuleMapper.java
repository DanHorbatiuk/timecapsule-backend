package dev.horbatiuk.timecapsule.persistence.mapper;

import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleCreateDTO;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleResponseDTO;
import dev.horbatiuk.timecapsule.persistence.entities.Capsule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = AttachmentMapper.class)
public interface CapsuleMapper {

    @Mapping(source = "appUser.name", target = "username")
    @Mapping(source = "appUser.email", target = "email")
    CapsuleResponseDTO toResponseDTO(Capsule capsule);

    @Mapping(target = "id",  ignore = true)
    @Mapping(target = "createdAt",  ignore = true)
    @Mapping(target = "status",  ignore = true)
    @Mapping(target = "appUser",  ignore = true)
    Capsule toEntity(CapsuleCreateDTO capsuleCreateDTO);

}
