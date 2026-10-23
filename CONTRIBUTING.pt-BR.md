[English](CONTRIBUTING.md) · [Português (BR)](CONTRIBUTING.pt-BR.md) · [Español](CONTRIBUTING.es.md)

# Contribuindo com o LeakWeb

Obrigado pelo interesse em contribuir! Antes de abrir um Pull Request, leia o
seguinte.

## Como contribuir

1. Abra uma Issue descrevendo o bug ou a funcionalidade proposta, para
   alinhar antes de codar (evita retrabalho).
2. Faça um fork do repositório e crie uma branch a partir da `main`.
3. Siga o estilo de código já usado no projeto (Kotlin, Detekt e ktlint via Spotless).
4. Rode `./gradlew spotlessApply` para formatar o código e adicionar o
   cabeçalho dos arquivos, e depois `./gradlew build` (compilação, testes
   unitários, Detekt, Spotless e lint) antes de abrir o PR.
5. Adicione ou atualize testes para o que você alterou.
6. Descreva claramente, na descrição do PR, o que foi alterado e por quê.

Requisitos: Android SDK e JDK 17+ (veja o [README](README.pt-BR.md#compilando)).

## Regra de "sala limpa"

O LeakWeb é escrito do zero. **Não envie código copiado ou adaptado de
projetos cuja licença seja incompatível com o
modelo de dupla licença deste projeto** (por exemplo, código somente GPL).
As contribuições devem ser trabalho original seu, ou vir de fontes sob
licenças permissivas (MIT, BSD, Apache-2.0, ISC) com os avisos preservados.

Novas dependências também precisam ter licença compatível com as duas opções
(Apache-2.0, MIT, BSD ou similar). Dependências GPL e AGPL não são aceitas.

## Licenciamento das contribuições e Contributor Agreement

Este projeto usa um modelo de **dupla licença**: é disponibilizado
publicamente sob a [AGPLv3](LICENSE), e também é oferecido sob uma
[licença comercial separada](COMMERCIAL-LICENSE.pt-BR.md) para quem não pode
cumprir os termos da AGPLv3.

Para que esse modelo funcione legalmente, é necessário que o mantenedor do
projeto (Roger Philippe Pereira) detenha os direitos necessários para
relicenciar comercialmente o código completo, incluindo contribuições de
terceiros.

**Ao enviar uma contribuição (Pull Request, patch, ou qualquer outro
material) para este projeto, você concorda que:**

1. Sua contribuição é licenciada sob a AGPLv3, como o restante do projeto; e
2. Você concede ao mantenedor do projeto (Roger Philippe Pereira) uma licença
   perpétua, mundial, não exclusiva e livre de royalties para também
   relicenciar sua contribuição sob termos comerciais, como parte do modelo
   de dupla licença descrito em [COMMERCIAL-LICENSE.pt-BR.md](COMMERCIAL-LICENSE.pt-BR.md); e
3. Você declara ter o direito de fazer essa contribuição e essa concessão
   (por exemplo, que o código é seu ou que você tem autorização do
   empregador, se aplicável).

O template de pull request pede que você confirme esse acordo. Se você não
concordar com esses termos, infelizmente não podemos aceitar sua contribuição
neste projeto.
