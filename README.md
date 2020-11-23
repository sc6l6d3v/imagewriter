# Image Writer

Working Scala REST backend to allow quick modification of stock image.

### Docker

To run in production mode inside a Docker container we first have to build the image. E.g.

```
docker build -t imagewriter:rest .
```

The aforementioned command will build the image and tag it with the latest commit hash.

To run said image:

```
docker run -d -p 8080:8080 imagewriter:rest
```

To attach to said image via shell:

```
docker exec -it <imagehash> /bin/bash
```

REST syntax
GET http://localhost:8080/static/<image>/content

where image is a single digit from 0-3 and content is URLencoded text with "@" as a line delimiter.
