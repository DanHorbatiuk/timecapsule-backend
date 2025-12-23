package dev.horbatiuk.timecapsule.persistence.mapper;

import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentCreateDTO;
import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentResponseDTO;
import dev.horbatiuk.timecapsule.persistence.entities.Attachment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AttachmentMapper {

    @Mapping(source = "capsule.id", target = "capsuleId")
    AttachmentResponseDTO toDTO(Attachment attachment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "capsule", ignore = true)
    Attachment toEntity(AttachmentCreateDTO attachmentCreateDTO);

}
