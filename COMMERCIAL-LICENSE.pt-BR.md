[English](COMMERCIAL-LICENSE.md) · [Português (BR)](COMMERCIAL-LICENSE.pt-BR.md) · [Español](COMMERCIAL-LICENSE.es.md)

# Licenciamento Comercial

O LeakWeb é um software de código aberto, distribuído sob **dupla licença**:

1. **GNU Affero General Public License v3.0 (AGPLv3)** — gratuita, para qualquer
   pessoa ou empresa que use, modifique e redistribua o projeto (ou o ofereça
   como serviço em rede) **desde que cumpra os termos da AGPLv3**, o que inclui
   disponibilizar o código-fonte completo de qualquer versão modificada, sob a
   mesma licença, a quem receber o software ou usar o serviço.

2. **Licença comercial**, concedida **apenas a critério exclusivo do
   mantenedor**, para quem quiser usar o LeakWeb de formas que a AGPLv3 não
   permite, por exemplo:
   - Vender o LeakWeb, ou uma versão modificada ou com outra marca, como
     produto de código fechado;
   - Incorporar o LeakWeb (ou partes dele) em software proprietário ou em um
     SDK de código fechado distribuído a terceiros;
   - Distribuir versões modificadas sem compartilhar o código-fonte.

## Finalidade

A licença comercial existe como **garantia**, não como produto à venda. O
LeakWeb foi feito para continuar livre e aberto. Esta opção assegura que
ninguém possa pegar o projeto e vendê-lo como software de código fechado sem
a autorização expressa do mantenedor.

## Usando o LeakWeb no seu app

O LeakWeb é uma ferramenta de depuração, pensada para builds `debug`
(`debugImplementation`). As obrigações da AGPLv3 valem quando você **repassa**
o software a outras pessoas (por exemplo, entrega um APK que contém o LeakWeb
a quem está fora da sua organização) ou quando você o **modifica** e permite
que outros interajam com a versão modificada por meio de uma rede.

- Usar a biblioteca sem modificações em builds debug que ficam dentro da sua
  equipe não distribui o LeakWeb a ninguém.
- Builds de release não devem conter o LeakWeb: use o `leakweb-noop`, que tem
  a mesma API pública e uma implementação vazia.
- Se você modificar o LeakWeb, ou distribuir a terceiros um build que o
  contenha, cumpra a AGPLv3 ou peça uma licença comercial.

Esta seção é um resumo em linguagem simples e não é aconselhamento jurídico.
Em caso de dúvida, consulte um advogado ou fale com o mantenedor.

## Quando é preciso pedir autorização

Se você quer usar o LeakWeb de uma forma que a AGPLv3 **não permite sem abrir o
código correspondente**, é necessário obter autorização por escrito do
mantenedor. Os pedidos podem ser recusados.

## Contato

Roger Philippe Pereira — rogerphilippepereira@gmail.com

## Sobre contribuições

Ao contribuir com código para este projeto, você concorda com os termos
descritos em [CONTRIBUTING.pt-BR.md](CONTRIBUTING.pt-BR.md), que incluem a
concessão ao mantenedor do direito de relicenciar sua contribuição — isso é
o que torna esse modelo de dupla licença possível.
