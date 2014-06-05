$ ->
  makeSvg = (jsonAddress) ->
    width = $("#chart").width()
    height = $("#chart").height()

    badness = d3.scale.linear().domain([-1, 300]).range(["green", "red"])
    color = (d) ->
      badness(d["metric--lines-of-code"])

    strength = (link) ->
      switch link.kind
        when "imports" then 0.01
        when "runtime-calls" then 0.01
        when "calls" then 0.01
        when "package-runtime-calls" then 0.01
        when "package-calls" then 0.01
        when "package-imports" then 0.03
        when "in-package" then 1.0

    linkColor = (link) ->
      switch link.kind
        when "in-package" then "#cc0000"
        when "imports" then "#d3d7df"
        when "package-imports" then "#babdb6"
        when "package-calls" then "#f7ad00"
        when "package-runtime-calls" then "#fce94f"
        when "calls" then "#f7ad00"
        when "runtime-calls" then "#fce94f"

    linkWidth = (link) ->
      switch link.kind
        when "in-package" then 1.5
        when "package-imports" then 1
        when "package-calls" then Math.min(link.count / 10.0, 5)
        when "package-runtime-calls" then Math.min(link.count / 10.0, 5)
        when "calls" then Math.min(link.count / 10.0, 5)
        when "runtime-calls" then Math.min(link.count / 10.0, 5)
        when "imports" then 1

    force = d3.layout.force()
      .charge(-120)
      .linkDistance(30)
      .linkStrength(strength)
      .size([width, height])
      .gravity(0.2)

    svg = d3
      .select("#chart")
      .append("svg:svg")
      .attr("width", width)
      .attr("height", height)
      .attr("pointer-events", "all")
      .append("svg:g")
      .call(d3.behavior.zoom().on("zoom", ->
        svg.attr("transform", "translate(#{d3.event.translate}) scale(#{d3.event.scale})")
      ))
      .append("svg:g")

    svg
      .append("svg:rect")
      .attr("width", width)
      .attr("height", height)
      .attr("fill", "transparent")
      .attr("pointer-events", "all")

    d3.json jsonAddress, (json) ->
      force
        .nodes(json.nodes)
        .links(json.edges)
        .start()

      link = svg.selectAll("line.link")
        .data(json.edges)
        .enter()
        .append("svg:line")
        .attr("class", "link")
        .style("stroke-width", linkWidth)
        .style("stroke", linkColor)


      linkedByIndex = {}
      json.edges.forEach((d) -> linkedByIndex[d.source.index + "," + d.target.index] = 1)

      isConnected = (a, b) ->
        linkedByIndex[a.index + "," + b.index] || linkedByIndex[b.index + "," + a.index] || a.index == b.index

      node = svg.selectAll("circle.node")
        .data(json.nodes)
        .enter()
        .append("circle")
        .attr("class", "node")
        .attr("r", (d) -> 3 + Math.max(3, 100.0 * d["page-rank"]))
        .style("fill", color)
        .call(force.drag)

      node
        .append("title")
        .text((d) -> d.name)

      svg
        .style("opacity", 1e-6)
        .transition()
        .duration(1000)
        .style("opacity", 1)

      force.on "tick", ->
        link
          .attr("x1", (d) -> d.source.x)
          .attr("y1", (d) -> d.source.y)
          .attr("x2", (d) -> d.target.x)
          .attr("y2", (d) -> d.target.y)
        node
          .attr("cx", (d) -> d.x)
          .attr("cy", (d) -> d.y)

  customSvg = (jsonAddress) ->
    width = $("#chart").width()
    height = $("#chart").height()

    badness = d3.scale.linear().domain([-1, 300]).range(["green", "red"])

    defaultStrengths =
      inPackage: 0.5
      packageImports: 0.1
      packageCalls: 0.01
      packageRuntimeCalls: 0.01
    strengths =
      inPackage: defaultStrengths.inPackage
      packageImports: defaultStrengths.packageImports
      packageCalls: defaultStrengths.packageCalls
      packageRuntimeCalls: defaultStrengths.packageRuntimeCalls
    strength = (link) ->
      switch link.kind
        when "package-imports" then strengths.packageImports
        when "in-package" then strengths.inPackage
        when "package-calls" then strengths.packageCalls
        when "package-runtime-calls" then strengths.packageRuntimeCalls

    defaultLinkColors =
      inPackage: "#cc0000"
      packageImports: "#babdb6"
      packageCalls: "#f7ad00"
      packageRuntimeCalls: "#fce94f"
    linkColors =
      inPackage: "transparent"
      packageImports: "transparent"
      packageCalls: "transparent"
      packageRuntimeCalls: "transparent"
    linkColor = (link) ->
      switch link.kind
        when "in-package" then linkColors.inPackage
        when "package-imports" then linkColors.packageImports
        when "package-calls" then linkColors.packageCalls
        when "package-runtime-calls" then linkColors.packageRuntimeCalls

    linkWidth = (link) ->
      switch link.kind
        when "in-package" then 1.5
        when "package-imports" then 1
        when "imports" then 1
        when "package-calls" then Math.min(link.count / 10.0, 5)
        when "package-runtime-calls" then Math.min(link.count / 10.0, 5)

    force = d3.layout.force()
    .charge(-120)
    .linkDistance(30)
    .linkStrength(0)
    .size([width, height])
    .gravity(0.2)

    svg = d3
    .select("#chart")
    .append("svg:svg")
    .attr("width", width)
    .attr("height", height)
    .attr("pointer-events", "all")
    .append("svg:g")
    .call(d3.behavior.zoom().on("zoom", ->
      svg.attr("transform", "translate(#{d3.event.translate}) scale(#{d3.event.scale})")
    ))
    .append("svg:g")

    svg
    .append("svg:rect")
    .attr("width", width)
    .attr("height", height)
    .attr("fill", "transparent")
    .attr("pointer-events", "all")

    d3.json jsonAddress, (json) ->
      force
      .nodes(json.nodes)
      .links(json.edges)
      .start()

      link = svg.selectAll("line.link")
      .data(json.edges)
      .enter()
      .append("svg:line")
      .attr("class", "link")
      .style("stroke-width", linkWidth)
      .style("stroke", "transparent")

      check = (selector, attr) ->
        $(selector).on "click", ->
          if ($(this).is(":checked"))
            linkColors[attr] = defaultLinkColors[attr]
            strengths[attr] = defaultStrengths[attr]
          else
            linkColors[attr] = "transparent"
            strengths[attr] = 0
          link.style("stroke", linkColor)
          force.linkStrength(strength).start()
        $(selector).triggerHandler("click")
      check(".check-imports", "packageImports")
      check(".check-contains", "inPackage")
      check(".check-calls", "packageCalls")
      check(".check-runtime-calls", "packageRuntimeCalls")

      linkedByIndex = {}
      json.edges.forEach((d) -> linkedByIndex[d.source.index + "," + d.target.index] = 1)

      isConnected = (a, b) ->
        linkedByIndex[a.index + "," + b.index] || linkedByIndex[b.index + "," + a.index] || a.index == b.index

      node = svg.selectAll("circle.node")
      .data(json.nodes)
      .enter()
      .append("circle")
      .attr("class", "node")
      .attr("r", 5)
      .style("fill", "#000000")
      .call(force.drag)

      $("""input[name="node-color"]""").on "click", ->
        $this = $(this)
        if ($this.is(":checked") and $this.attr("value") == "black")
          color = "#000000"
        else
          color = (d) -> badness(d["metric--lines-of-code"])
        node.style("fill", color).call(force.drag)
      $("""input[name="node-color"]""").triggerHandler("click")

      $("""input[name="node-size"]""").on "click", ->
        $this = $(this)
        if ($this.is(":checked") and $this.attr("value") == "constant")
          size = 5
        else
          size = (d) -> 3 + Math.max(3, 100.0 * d["page-rank"])
        node.attr("r", size).call(force.drag)
      $("""input[name="node-size"]""").triggerHandler("click")

      node
      .append("title")
      .text((d) -> d.name)

      svg
      .style("opacity", 1e-6)
      .transition()
      .duration(1000)
      .style("opacity", 1)

      force.on "tick", ->
        link
        .attr("x1", (d) -> d.source.x)
        .attr("y1", (d) -> d.source.y)
        .attr("x2", (d) -> d.target.x)
        .attr("y2", (d) -> d.target.y)
        node
        .attr("cx", (d) -> d.x)
        .attr("cy", (d) -> d.y)

  clearSvg = ->
    $("#chart").empty()

  checkOptimized = (jsonAddress, custom) ->
    $(".check-optimized").unbind("click.svg")
    $(".check-optimized").on "click.svg", ->
      clearSvg()
      if ($(".check-optimized").is(":checked"))
        versionJsonAddress = jsonAddress
      else
        versionJsonAddress = "0/" + jsonAddress
      if (custom)
        customSvg(versionJsonAddress)
      else
        makeSvg(versionJsonAddress)
    $(".check-optimized").triggerHandler("click")

  $(".custom-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-custom-tab").addClass("active")
    $(".gauges").remove()
    $(".mag-sidenav .active").after(
      """
      <li class="active gauges">
        <a href="#">
          <form>
            <div class="control-group">
              <label class="control-label">Edge</label>
              <div class="controls">
                <label class="checkbox inline">
                  <input type="checkbox" value="" class="check-imports"/>
                  imports
                </label>
                <label class="checkbox inline">
                  <input type="checkbox" value="" class="check-contains"/>
                  contains
                </label>
                <label class="checkbox inline">
                  <input type="checkbox" value="" class="check-calls"/>
                  calls
                </label>
                <label class="checkbox inline">
                  <input type="checkbox" value="" class="check-runtime-calls"/>
                  runtime calls
                </label>
              </div>
            </div>
            <div class="control-group">
              <label class="control-label">Node size</label>
              <div class="controls">
                <label class="radio">
                  <input type="radio" name="node-size" value="constant" checked="checked"/>
                  Constant
                </label>
                <label class="radio">
                  <input type="radio" name="node-size" value="page-rank"/>
                  Page rank
                </label>
              </div>
            </div>
            <div class="control-group">
              <label class="control-label">Node color</label>
              <div class="controls">
                <label class="radio">
                  <input type="radio" name="node-color" value="black" checked="checked"/>
                  Black
                </label>
                <label class="radio">
                  <input type="radio" name="node-color" value="by-avg-loc"/>
                  Avg. lines of code / class
                </label>
                <!--<label class="radio">
                  <input type="radio" value=""/>
                  Avg number of methods / class
                </label>
                <label class="radio">
                  <input type="radio" value=""/>
                  Classes total
                </label>-->
              </div>
            </div>
          </form>
        </a>
      </li>
      <li class="active gauges">
        <a href="javascript:;">
          <label class="control-label pagination-centered"><strong>Optimize</strong></label>
          <form class="form">
            <div class="control-group">
              <label class="control-label" for="iterations">Iterations</label>
              <div class="controls">
                <input class="span6 iterations" type="text" name="iterations" placeholder="100">
              </div>
            </div>
            <div class="control-group">
              <label class="control-label" for="tolerance">Tolerance</label>
              <div class="controls">
                <input class="span6 tolerance" type="text" name="tolerance" placeholder="5">
              </div>
            </div>
            <div class="control-group">
              <div class="controls">
                <label class="checkbox inline">
                  <input type="checkbox" value="" class="check-incremental"/>
                  incremental
                </label>
              </div>
            </div>
            <div class="control-group">
              <div class="controls">
                <span class="btn btn-default optimize-button">Go!</span>
              </div>
            </div>
          </form>
          <label class="optimization-status" />
        </a>
      </li>
      """)
    $(".iterations").val($(".iterations").attr("placeholder"))
    $(".tolerance").val($(".tolerance").attr("placeholder"))
    $(".optimize-button").on "click", ->
      jsRoutes.controllers.OptimizeGraph.optimize($("#projectName").text(), $(".iterations").val(), $(".tolerance").val(), $(".incremental").is(":checked")).ajax({
        success: (data) ->
          $(".optimization-status").removeClass((index, css) ->
            (css.match(/\btext-\S+/g) || []).join(' ')
          )
          $(".optimization-status").addClass("text-success")
          $(".optimization-status").text("")
          $(".check-optimized").prop("disabled", false)
          $(".check-optimized").prop("checked", true)
          $(".check-optimized").triggerHandler("click")
        error: (data) ->
          $(".optimization-status").removeClass((index, css) ->
            (css.match(/\btext-\S+/g) || []).join(' ')
          )
          $(".optimization-status").addClass("text-error")
          $(".optimization-status").text("Optimization failed!")
          $(".check-optimized").prop("checked", false)
          $(".check-optimized").prop("disabled", true)
          $(".check-optimized").triggerHandler("click")
      })
      $(".optimization-status").removeClass((index, css) ->
        (css.match(/\btext-\S+/g) || []).join(' ')
      )
      $(".optimization-status").addClass("text-warning")
      $(".optimization-status").text("Optimizing...")
    checkOptimized("custom.json", true)

  $(".packages-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-packages-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("packages.json", false)
    $("[rel='tooltip']").tooltip()

  $(".package-imports-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-package-imports-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("pkgImports.json", false)
    $("[rel='tooltip']").tooltip()

  $(".package-calls-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-package-calls-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("pkgCalls.json", false)
    $("[rel='tooltip']").tooltip()

  $(".class-calls-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-class-calls-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("classCalls.json", false)
    $("[rel='tooltip']").tooltip()

  jsRoutes.controllers.ShowGraph.versionsJson($("#projectName").text()).ajax({
    success: (data) ->
      $(".check-optimized").prop("disabled", data.versions.length <= 1)
    error: (data) ->
      $(".check-optimized").prop("checked", false)
      $(".check-optimized").prop("disabled", true)
  })

  $(".packages-button").triggerHandler("click")
  $("[rel='tooltip']").tooltip()
